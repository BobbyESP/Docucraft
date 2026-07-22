/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.internal.engine

import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import com.composepdf.internal.service.pdf.PdfDocumentManager
import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "PdfRenderEngine"

/**
 * The rendering engine: one planner, a small pool of render workers, and two stores.
 *
 * ## Flow
 * Every viewport change calls [requestPlan]. Requests land in a conflated channel, so the planner
 * always works from the *latest* viewport and never queues up. Each pass produces a [RenderPlan]:
 * the plan replaces the work queue wholesale (implicit cancellation of everything obsolete),
 * updates publication/retention in the stores, and workers immediately start on the
 * highest-priority missing content.
 *
 * ## Staleness
 * A single [epoch] counter guards the whole pipeline. [invalidate] bumps it, clears the queue and
 * stores, and any in-flight result from an older epoch is returned to the pool instead of being
 * published. Within an epoch, workers re-validate each tile against the live plan right before
 * rasterizing it, so panning away stops wasted work at tile granularity.
 *
 * ## Concurrency
 * Worker count matches the [PdfDocumentManager] renderer pool, so no worker ever blocks on a
 * renderer permit while another renderer sits idle. Tiles of the same page are batched under a
 * single page open, which is where most of the old engine's tile latency came from.
 */
internal class RenderEngine(
    private val documentManager: PdfDocumentManager,
    private val pool: BitmapPool,
    private val inputsProvider: () -> PlanInputs?,
) : Closeable {

    private val pageRenderer = PageRenderer(pool)
    private val planComputer = PlanComputer()

    val tileStore = TileStore(pool)
    val pageStore = PageBitmapStore(pool)

    val baseBitmaps: StateFlow<Map<Int, ImageBitmap>> = pageStore.published
    val tiles: StateFlow<Map<Int, List<TileDraw>>> = tileStore.published

    private val renderDispatcher =
        Executors.newFixedThreadPool(WORKER_COUNT).asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val planSignal = Channel<Unit>(Channel.CONFLATED)
    private val epoch = AtomicInteger(0)

    @Volatile private var plan: RenderPlan = RenderPlan.EMPTY

    private val queue = WorkQueue(WORKER_COUNT)
    private val inFlightTiles = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()
    private val inFlightBasePages = java.util.concurrent.ConcurrentHashMap<Int, Int>()

    init {
        scope.launch { for (unit in planSignal) runPlanPass() }
        repeat(WORKER_COUNT) { scope.launch(renderDispatcher) { workerLoop() } }
    }

    /** Cheap and safe to call on every gesture frame. */
    fun requestPlan() {
        planSignal.trySend(Unit)
    }

    /**
     * Drops every cached bitmap and all pending/in-flight results. Use on document/layout change.
     */
    fun invalidate() {
        epoch.incrementAndGet()
        plan = RenderPlan.EMPTY
        queue.clear()
        tileStore.clear()
        pageStore.clear()
        requestPlan()
    }

    private fun runPlanPass() {
        if (!documentManager.isOpen) return
        val inputs = inputsProvider() ?: return
        val currentEpoch = epoch.get()

        val newPlan =
            planComputer.compute(
                inputs = inputs,
                epoch = currentEpoch,
                isTileAvailable = { packed ->
                    tileStore.contains(packed) || packed in inFlightTiles
                },
                isBaseAvailable = { page, width ->
                    pageStore.widthOf(page) == width || inFlightBasePages[page] == width
                },
            )
        if (currentEpoch != epoch.get()) return

        plan = newPlan
        pageStore.applyPlan(newPlan)
        // While flying fast we keep whatever tiles are on screen instead of churning them.
        if (!newPlan.tilesFrozen) tileStore.applyPlan(newPlan)
        queue.replace(newPlan.work, currentEpoch)
    }

    private suspend fun workerLoop() {
        while (currentCoroutineContext().isActive) {
            val (work, workEpoch) = queue.take()
            if (workEpoch != epoch.get()) continue
            try {
                when (work) {
                    is RenderWork.BasePage -> renderBasePage(work, workEpoch)
                    is RenderWork.Tile -> renderTileBatch(work, workEpoch)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Render work failed for page ${work.pageIndex}", e)
            }
        }
    }

    private suspend fun renderBasePage(work: RenderWork.BasePage, workEpoch: Int) {
        inFlightBasePages[work.pageIndex] = work.targetWidth
        try {
            val bitmap =
                documentManager.withPage(work.pageIndex) { page ->
                    pageRenderer.renderBasePage(page, work.targetWidth, work.targetHeight)
                }
            if (workEpoch == epoch.get()) {
                pageStore.put(work.pageIndex, work.targetWidth, bitmap)
            } else {
                pool.put(bitmap)
            }
        } finally {
            inFlightBasePages.remove(work.pageIndex)
        }
    }

    private suspend fun renderTileBatch(first: RenderWork.Tile, workEpoch: Int) {
        val batch = ArrayList<RenderWork.Tile>(MAX_TILE_BATCH)
        batch += first
        batch += queue.drainTilesForPage(first.pageIndex, MAX_TILE_BATCH - 1)
        for (tile in batch) inFlightTiles.add(tile.id.packed)
        try {
            documentManager.withPage(first.pageIndex) { page ->
                for (tile in batch) {
                    if (workEpoch != epoch.get()) break
                    val livePlan = plan
                    val stillWanted =
                        livePlan.tilesFrozen ||
                            livePlan.desiredTilesByPage[tile.pageIndex]?.contains(tile.id.packed) ==
                                true
                    if (!stillWanted) {
                        inFlightTiles.remove(tile.id.packed)
                        continue
                    }
                    val bitmap =
                        pageRenderer.renderTile(page, tile.rect, tile.levelScale, tile.baseWidth)
                    if (workEpoch == epoch.get()) {
                        tileStore.put(tile.id, tile.rect, tile.levelScale, bitmap)
                    } else {
                        pool.put(bitmap)
                    }
                    inFlightTiles.remove(tile.id.packed)
                }
            }
        } finally {
            for (tile in batch) inFlightTiles.remove(tile.id.packed)
        }
    }

    override fun close() {
        epoch.incrementAndGet()
        scope.cancel()
        queue.clear()
        tileStore.clear()
        pageStore.clear()
        pool.clear()
        renderDispatcher.close()
    }

    companion object {
        /**
         * Matches [PdfDocumentManager]'s renderer pool size: more workers than renderers would only
         * park threads on the acquisition semaphore.
         */
        const val WORKER_COUNT = 2

        /** Upper bound of tiles rendered per page open. */
        const val MAX_TILE_BATCH = 4
    }
}
