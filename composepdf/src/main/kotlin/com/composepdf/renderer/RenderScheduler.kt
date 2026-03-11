package com.composepdf.renderer

import android.graphics.Bitmap
import android.util.Log
import android.util.Size
import com.composepdf.cache.PageCacheKey
import com.composepdf.cache.TileDiskCache
import com.composepdf.cache.bitmap.BitmapCache
import com.composepdf.cache.bitmap.BitmapPool
import com.composepdf.renderer.tiles.TileKey
import com.composepdf.state.PdfViewerState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
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
import java.util.concurrent.Executors
import java.util.concurrent.PriorityBlockingQueue
import kotlin.math.abs
import kotlin.math.roundToInt

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
    val cacheKey: String,
    val pageIndex: Int,
    val sessionToken: Int,
    val pageCacheKey: PageCacheKey? = null,
    val action: suspend (snapshot: RenderWindowSnapshot) -> Unit
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
    private val cache: BitmapCache,
    private val viewerState: PdfViewerState,
    private val bitmapPool: BitmapPool,
    private val tileDiskCache: TileDiskCache? = null,
    private val telemetry: RenderTelemetry? = null
) : Closeable {

    private val renderDispatcher = Executors.newFixedThreadPool(3).asCoroutineDispatcher()
    private val scope = CoroutineScope(renderDispatcher + SupervisorJob())
    private val renderWindowTracker = RenderWindowTracker()

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
                        val currentSnapshot = renderWindowTracker.getSnapshot()

                        // 1. Session check: Instant drop if document changed.
                        if (task.sessionToken != currentSnapshot.sessionToken) continue

                        // 2. Relevancy check: Is this specific key still wanted?
                        val isStillWanted = if (task.pageCacheKey != null) {
                            currentSnapshot.desiredPages[task.pageIndex] == task.pageCacheKey
                        } else {
                            task.cacheKey in currentSnapshot.desiredTiles
                        }

                        if (isStillWanted) {
                            task.action(currentSnapshot)
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

    /**
     * Reclaims memory from pages that are significantly far from the current viewport.
     * This prevents OOM during rapid navigation across large documents.
     */
    private fun proactivelyEvictFarPages(visiblePages: IntRange, totalPages: Int) {
        val keepRadius = prefetchWindow + 5 // Margin to avoid immediate re-rendering
        val minKeep = (visiblePages.first - keepRadius).coerceAtLeast(0)
        val maxKeep = (visiblePages.last + keepRadius).coerceAtMost(totalPages - 1)
        cache.clearPagesOutside(minKeep..maxKeep)
    }

    /**
     * Removes tasks from the queue that belong to a previous document session.
     *
     * To maintain performance, this cleanup only triggers if the queue exceeds
     * a threshold (50 tasks), preventing unnecessary iterations on small workloads
     * while ensuring memory is not wasted on outdated rendering requests after
     * rapid document changes or resets.
     */
    private fun pruneObsoleteTasks() {
        val currentSession = renderWindowTracker.currentSessionToken()
        if (taskQueue.size > 50) {
            taskQueue.removeIf { it.sessionToken < currentSession }
        }
    }

    fun onDocumentLoaded(documentKey: String) {
        docKey = documentKey
        renderWindowTracker.beginNewSession()
        taskQueue.clear()
        _renderedPages.value = emptyMap()
    }

    fun updateTileWindow(keepKeys: Set<String>) {
        renderWindowTracker.updateDesiredTiles(keepKeys)
    }

    /**
     * Schedules rendering for base pages within and surrounding the active viewport.
     *
     * This method manages the lifecycle of low-resolution page bitmaps by:
     * 1. Pruning obsolete tasks and proactively evicting far-away pages from memory.
     * 2. Calculating the active window (visible pages plus [prefetchWindow]).
     * 3. Syncing the [RenderWindowTracker] with current [PageCacheKey] requirements.
     * 4. Dispatching prioritized [RenderTask]s for missing pages while publishing cached ones.
     *
     * @param visiblePages The range of page indices currently visible to the user.
     * @param config The rendering configuration including zoom level and quality settings.
     * @param pageSizes A list of intrinsic sizes for all pages in the document.
     * @param getBaseWidth A provider for the reference width of a specific page.
     * @param renderPassId A unique identifier used for telemetry and tracking this specific update.
     */
    fun requestRender(
        visiblePages: IntRange,
        config: PageRenderer.RenderConfig,
        pageSizes: List<Size>,
        getBaseWidth: (Int) -> Float,
        renderPassId: Int
    ) {
        if (!documentManager.isOpen || pageSizes.isEmpty()) return

        pruneObsoleteTasks()
        proactivelyEvictFarPages(visiblePages, pageSizes.size)

        val sessionToken = renderWindowTracker.currentSessionToken()
        val roundedZoom = (config.zoomLevel * 100f).roundToInt() / 100f

        val winStart = (visiblePages.first - prefetchWindow).coerceAtLeast(0)
        val winEnd = (visiblePages.last + prefetchWindow).coerceAtMost(pageSizes.size - 1)
        val activeWindow = winStart..winEnd

        // Remove base bitmaps for pages that left the prefetch window.
        _renderedPages.update { current -> current.filterKeys { it in activeWindow } }

        val desiredPages = activeWindow.associateWith { pageIndex ->
            val size = pageSizes[pageIndex]
            val (targetW, targetH) = pageRenderer.calculateRenderSize(
                size.width, size.height, getBaseWidth(pageIndex), config
            )
            PageCacheKey(pageIndex, roundedZoom, targetW, targetH)
        }
        renderWindowTracker.updateDesiredPages(desiredPages)

        for (pageIndex in activeWindow) {
            val cacheKey = desiredPages[pageIndex] ?: continue
            val cached = cache.get(cacheKey)

            if (cached != null) {
                publishBitmap(pageIndex, cached)
                telemetry?.recordPageMemoryHit(renderPassId, pageIndex, config.zoomLevel)
                continue
            }

            val priority =
                if (pageIndex in visiblePages) RenderPriority.VISIBLE_LOW_RES else RenderPriority.PREFETCH_LOW_RES

            taskQueue.offer(
                RenderTask(
                    priority = priority,
                    distanceSq = abs(pageIndex - (visiblePages.first + visiblePages.last) / 2).toFloat(),
                    spatialIndex = pageIndex, // Here makes no sense yo use morton
                    cacheKey = cacheKey.toString(),
                    pageIndex = pageIndex,
                    sessionToken = sessionToken,
                    pageCacheKey = cacheKey,
                    action = { latestSnapshot ->
                        try {
                            val bitmap = documentManager.withPage(pageIndex) { page ->
                                pageRenderer.render(page, getBaseWidth(pageIndex), config)
                            }
                            cache.put(cacheKey, bitmap)

                            // Double check relevancy before updating UI flow
                            if (latestSnapshot.sessionToken == sessionToken && latestSnapshot.desiredPages[pageIndex] == cacheKey) {
                                publishBitmap(pageIndex, bitmap)
                            }
                            telemetry?.recordPagePublished(renderPassId, pageIndex, false)
                        } catch (e: Exception) {
                            if (e !is CancellationException) Log.e(
                                TAG,
                                "Page render failed: $pageIndex"
                            )
                        }
                    }
                ))
        }
    }

    /**
     * Schedules the rendering of a high-resolution tile for a specific section of a page.
     *
     * The task is added to a priority queue where visible tiles take precedence over prefetch tiles.
     * The rendering process first attempts to retrieve the tile from [tileDiskCache] before
     * falling back to rasterizing the PDF page.
     *
     * To ensure memory efficiency, the resulting bitmap is only published to the [viewerState]
     * if the tile is still within the active render window snapshot; otherwise, it is
     * immediately returned to the [bitmapPool].
     *
     * @param tileKey The unique identifier for the tile, containing page index, zoom, and coordinates.
     * @param baseWidth The reference width of the page used for scaling calculations.
     * @param distanceSq The squared distance from the viewport center, used for intra-priority sorting.
     * @param isPrefetch If true, assigns a lower priority ([RenderPriority.PREFETCH_TILE]) to the task.
     * @param renderPassId An identifier for telemetry tracking and performance profiling.
     */
    internal fun requestTile(
        tileKey: TileKey,
        baseWidth: Float,
        distanceSq: Float,
        isPrefetch: Boolean,
        renderPassId: Int
    ) {
        val sessionToken = renderWindowTracker.currentSessionToken()
        val memoryKey = tileKey.toCacheKey()

        if (viewerState.getTile(memoryKey) != null) return

        val priority = if (isPrefetch) RenderPriority.PREFETCH_TILE else RenderPriority.VISIBLE_TILE

        taskQueue.offer(
            RenderTask(
                priority = priority,
                distanceSq = distanceSq,
                cacheKey = memoryKey,
                spatialIndex = morton(tileKey.tileX, tileKey.tileY),
                pageIndex = tileKey.pageIndex,
                sessionToken = sessionToken,
                action = { latestSnapshot ->
                    try {
                        val diskKey = tileKey.toDiskCacheKey(docKey)
                        val diskBitmap = tileDiskCache?.get(
                            docKey = docKey,
                            pageIndex = tileKey.pageIndex,
                            tileKey = diskKey
                        )

                        val bitmap = if (diskBitmap != null) {
                            telemetry?.recordTileDiskHit(renderPassId, memoryKey)
                            diskBitmap
                        } else {
                            val rendered = documentManager.withPage(tileKey.pageIndex) { page ->
                                pageRenderer.renderTile(page, tileKey.rect, tileKey.zoom, baseWidth)
                            }
                            scope.launch {
                                diskSemaphore.withPermit {
                                    tileDiskCache?.put(
                                        docKey = docKey,
                                        pageIndex = tileKey.pageIndex,
                                        tileKey = diskKey,
                                        bitmap = rendered
                                    )
                                }
                            }
                            telemetry?.recordTileRendered(renderPassId, memoryKey)
                            rendered
                        }

                        // Strict Publication: Only put in State if still in the keep window.
                        // Otherwise, return to pool immediately to prevent memory leaks.
                        if (latestSnapshot.sessionToken == sessionToken && memoryKey in latestSnapshot.desiredTiles) {
                            viewerState.putTile(memoryKey, bitmap)
                        } else {
                            bitmapPool.put(bitmap)
                        }
                    } catch (e: Exception) {
                        if (e !is CancellationException) Log.e(
                            TAG,
                            "Tile render failed: $memoryKey"
                        )
                    }
                }
            ))
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

    /**
     * Resets the entire rendering pipeline by clearing all pending tasks, caches, and current state.
     *
     * This method:
     * 1. Increments the session token to invalidate any in-flight rendering workers.
     * 2. Clears the high-priority and low-priority task queues.
     * 3. Wipes the current [renderedPages] state flow.
     * 4. Evicts all bitmaps from the memory cache.
     *
     * Use this when the document source changes or a global refresh of the rendered content is required.
     */
    fun invalidateAll() {
        renderWindowTracker.beginNewSession()
        taskQueue.clear()
        _renderedPages.value = emptyMap()
        cache.clear()
    }

    override fun close() {
        invalidateAll()
        scope.cancel()
        renderDispatcher.close()
    }
}
