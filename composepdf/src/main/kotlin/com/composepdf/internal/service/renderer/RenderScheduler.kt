package com.composepdf.internal.service.renderer

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import android.util.Size
import com.composepdf.PdfViewerState
import com.composepdf.internal.logic.tiles.TileKey
import com.composepdf.internal.service.cache.TileDiskCache
import com.composepdf.internal.service.cache.bitmap.BitmapPool
import com.composepdf.internal.service.pdf.PageRenderer
import com.composepdf.internal.service.pdf.PdfDocumentManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.Closeable
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

private const val TAG = "PdfRenderScheduler"

/**
 * Priority levels for rendering tasks.
 * Order matters: VISIBLE_TILE is 0 (highest priority).
 */
internal enum class RenderPriority {
    VISIBLE_TILE,
    VISIBLE_LOW_RES,
    PREFETCH_LOW_RES,
    PREFETCH_TILE
}

/**
 * Represents a single atomic rendering unit (a page or a tile).
 * Implements [Comparable] to be used in a [PriorityBlockingQueue].
 */
internal data class RenderTask(
    val priority: RenderPriority,
    val distanceSq: Float,
    val spatialIndex: Int,
    val tileKey: TileKey,
    val baseWidth: Float,
    val sessionToken: Int,
    val renderPassId: Int
) : Comparable<RenderTask> {
    override fun compareTo(other: RenderTask): Int {
        val priorityCompare = priority.ordinal.compareTo(other.priority.ordinal)
        if (priorityCompare != 0) return priorityCompare

        val distanceCompare = distanceSq.compareTo(other.distanceSq)
        if (distanceCompare != 0) return distanceCompare

        return spatialIndex.compareTo(other.spatialIndex)
    }
}

/**
 * Orchestrates background rendering tasks for both full pages and high-resolution tiles.
 *
 * This implementation focuses on:
 * 1. **Proactive Memory Management**: Force-evicts far-away pages from RAM cache.
 * 2. **Atomic Publication**: Uses [RenderWindowSnapshot] to prevent race conditions.
 * 3. **Strict Ownership**: Ensures every bitmap is either published or returned to [BitmapPool].
 */
internal class RenderScheduler internal constructor(
    private val documentManager: PdfDocumentManager,
    private val pageRenderer: PageRenderer,
    private val viewerState: PdfViewerState,
    private val bitmapPool: BitmapPool,
    private val tileDiskCache: TileDiskCache? = null,
    private val telemetry: RenderTelemetry? = null
) : Closeable {
    private val renderDispatcher = Dispatchers.IO.limitedParallelism(3)
    private val scope = CoroutineScope(renderDispatcher + SupervisorJob())

    private data class RenderWindowSnapshot(
        val sessionToken: Int,
        val desiredPages: Map<Int, TileKey>,
        val desiredTiles: Set<String>
    )

    private val snapshot = AtomicReference(
        RenderWindowSnapshot(
            sessionToken = 0,
            desiredPages = emptyMap(),
            desiredTiles = emptySet()
        )
    )

    private val taskQueue = PriorityBlockingQueue<RenderTask>()
    private val diskSemaphore = Semaphore(2)

    /** Number of pages to pre-render outside the visible range. */
    var prefetchWindow: Int = 2
        set(value) {
            field = value.coerceAtLeast(0)
        }

    /** Stable identifier for the currently open document. */
    var docKey: String = ""

    private val _renderedPages = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())

    /** Observed by the UI to display base page bitmaps. */
    val renderedPages: StateFlow<Map<Int, Bitmap>> = _renderedPages.asStateFlow()

    init {
        // Fixed pool of 3 workers for high-concurrency PDF rasterization.
        repeat(3) {
            scope.launch {
                while (isActive) {
                    try {
                        val task = taskQueue.take()
                        val currentSnapshot = snapshot.get()

                        if (task.sessionToken != currentSnapshot.sessionToken) continue

                        val tasksToProcess = mutableListOf(task)
                        val iterator = taskQueue.iterator()

                        var scanned = 0
                        while (iterator.hasNext() && scanned < 50) {
                            val next = iterator.next()
                            scanned++

                            if (next.tileKey.pageIndex == task.tileKey.pageIndex &&
                                next.sessionToken == task.sessionToken &&
                                next.priority == task.priority
                            ) {
                                tasksToProcess.add(next)
                                iterator.remove()
                            }
                            if (tasksToProcess.size >= 16) break
                        }

                        documentManager.withPage(task.tileKey.pageIndex) { page ->
                            tasksToProcess.forEach { currentTask ->
                                val tileKey = currentTask.tileKey
                                val memoryKey = tileKey.toCacheKey()

                                val snapshot = snapshot.get()
                                val isNeeded = if (tileKey.zoom == TileKey.BASE_LAYER_ZOOM) {
                                    snapshot.desiredPages[tileKey.pageIndex] == tileKey
                                } else {
                                    memoryKey in snapshot.desiredTiles
                                }

                                if (isNeeded) {
                                    try {
                                        val diskKey = tileKey.toDiskCacheKey(docKey)
                                        val diskBitmap = tileDiskCache?.get(
                                            docKey = docKey,
                                            pageIndex = tileKey.pageIndex,
                                            tileKey = diskKey
                                        )

                                        val bitmap = if (diskBitmap != null) {
                                            telemetry?.recordTileDiskHit(
                                                currentTask.renderPassId,
                                                memoryKey
                                            )
                                            diskBitmap
                                        } else {
                                            val rendered =
                                                if (tileKey.zoom == TileKey.BASE_LAYER_ZOOM) {
                                                    // Base Page Render
                                                    pageRenderer.render(
                                                        page,
                                                        currentTask.baseWidth,
                                                        PageRenderer.RenderConfig(
                                                            zoomLevel = 1.0f,
                                                            renderQuality = 1.0f
                                                        )
                                                    )
                                                } else {
                                                    // Tile Render
                                                    pageRenderer.renderTile(
                                                        page,
                                                        tileKey.rect,
                                                        tileKey.zoom,
                                                        currentTask.baseWidth
                                                    )
                                                }

                                            // Write to disk in background
                                            if (tileKey.zoom != TileKey.BASE_LAYER_ZOOM) {
                                                launch {
                                                    diskSemaphore.withPermit {
                                                        tileDiskCache?.put(
                                                            docKey = docKey,
                                                            pageIndex = tileKey.pageIndex,
                                                            tileKey = diskKey,
                                                            bitmap = rendered
                                                        )
                                                    }
                                                }
                                            }
                                            telemetry?.recordTileRendered(
                                                currentTask.renderPassId,
                                                memoryKey
                                            )
                                            rendered
                                        }

                                        if (tileKey.zoom == TileKey.BASE_LAYER_ZOOM) {
                                            publishBitmap(tileKey.pageIndex, bitmap)
                                        } else {
                                            viewerState.putTile(memoryKey, bitmap)
                                        }

                                    } catch (e: Exception) {
                                        Log.e(TAG, "Render failed for $memoryKey: ${e.message}")
                                    }
                                }
                            }
                        }
                    } catch (_: InterruptedException) {
                        break
                    } catch (e: Exception) {
                        if (e !is CancellationException) {
                            Log.e(TAG, "Worker error: ${e.message}", e)
                        }
                    }
                }
            }
        }
    }

    private fun pruneObsoleteTasks() {
        val currentSession = snapshot.get().sessionToken
        if (taskQueue.size > 50) {
            taskQueue.removeIf { it.sessionToken < currentSession }
        }
    }

    fun onDocumentLoaded(documentKey: String) {
        docKey = documentKey
        updateSnapshot { old ->
            RenderWindowSnapshot(
                sessionToken = old.sessionToken + 1,
                desiredPages = emptyMap(),
                desiredTiles = emptySet()
            )
        }
        taskQueue.clear()
        _renderedPages.value = emptyMap()
    }

    fun updateTileWindow(keepKeys: Set<String>) {
        updateSnapshot { it.copy(desiredTiles = keepKeys.toSet()) }
    }

    fun requestRender(
        visiblePages: IntRange,
        config: PageRenderer.RenderConfig,
        pageSizes: List<Size>,
        getBaseWidth: (Int) -> Float,
        renderPassId: Int
    ) {
        if (!documentManager.isOpen || pageSizes.isEmpty()) return

        pruneObsoleteTasks()

        val sessionToken = snapshot.get().sessionToken

        val winStart = (visiblePages.first - prefetchWindow).coerceAtLeast(0)
        val winEnd = (visiblePages.last + prefetchWindow).coerceAtMost(pageSizes.size - 1)
        val activeWindow = winStart..winEnd

        val dropped = mutableListOf<Bitmap>()
        _renderedPages.update { current ->
            val newMap = current.filterKeys { it in activeWindow }
            val droppedKeys = current.keys - newMap.keys
            droppedKeys.forEach { pageId ->
                current[pageId]?.let { bmp ->
                    dropped.add(bmp)
                }
            }
            newMap
        }

        if (dropped.isNotEmpty()) {
            scope.launch {
                dropped.forEach { bmp ->
                    if (!bmp.isRecycled) bitmapPool.put(bmp)
                }
            }
        }

        val desiredPages = activeWindow.associateWith { pageIndex ->
            TileKey(pageIndex, Rect(0, 0, 0, 0), TileKey.BASE_LAYER_ZOOM)
        }

        updateSnapshot { it.copy(desiredPages = desiredPages) }

        for (pageIndex in activeWindow) {
            val tileKey = desiredPages[pageIndex] ?: continue
            val cached = _renderedPages.value[pageIndex]

            if (cached != null) {
                telemetry?.recordPageMemoryHit(renderPassId, pageIndex, config.zoomLevel)
                continue
            }

            val priority =
                if (pageIndex in visiblePages) RenderPriority.VISIBLE_LOW_RES else RenderPriority.PREFETCH_LOW_RES

            taskQueue.offer(
                RenderTask(
                    priority = priority,
                    distanceSq = abs(pageIndex - (visiblePages.first + visiblePages.last) / 2).toFloat(),
                    spatialIndex = pageIndex,
                    tileKey = tileKey,
                    baseWidth = getBaseWidth(pageIndex),
                    sessionToken = sessionToken,
                    renderPassId = renderPassId
                )
            )
        }
    }

    internal fun requestTile(
        tileKey: TileKey,
        baseWidth: Float,
        distanceSq: Float,
        isPrefetch: Boolean,
        renderPassId: Int
    ) {
        val sessionToken = snapshot.get().sessionToken
        val memoryKey = tileKey.toCacheKey()

        if (viewerState.getTile(memoryKey) != null) return

        val priority = if (isPrefetch) RenderPriority.PREFETCH_TILE else RenderPriority.VISIBLE_TILE

        taskQueue.offer(
            RenderTask(
                priority = priority,
                distanceSq = distanceSq,
                spatialIndex = morton(tileKey.tileX, tileKey.tileY),
                tileKey = tileKey,
                baseWidth = baseWidth,
                sessionToken = sessionToken,
                renderPassId = renderPassId
            )
        )
    }

    private fun publishBitmap(pageIndex: Int, bitmap: Bitmap) {
        _renderedPages.update { current ->
            if (current[pageIndex] === bitmap) current
            else current + (pageIndex to bitmap)
        }
    }

    private fun morton(x: Int, y: Int, bits: Int = 16): Int {
        var answer = 0
        for (i in 0 until bits) {
            answer = answer or ((x shr i and 1) shl (2 * i))
            answer = answer or ((y shr i and 1) shl (2 * i + 1))
        }
        return answer
    }

    fun invalidateAll() {
        updateSnapshot { old ->
            RenderWindowSnapshot(
                sessionToken = old.sessionToken + 1,
                desiredPages = emptyMap(),
                desiredTiles = emptySet()
            )
        }
        taskQueue.clear()
        _renderedPages.value = emptyMap()
    }

    private fun updateSnapshot(transform: (RenderWindowSnapshot) -> RenderWindowSnapshot) {
        var old: RenderWindowSnapshot
        var next: RenderWindowSnapshot
        do {
            old = snapshot.get()
            next = transform(old)
        } while (!snapshot.compareAndSet(old, next))
    }

    override fun close() {
        invalidateAll()
        scope.cancel()
    }
}
