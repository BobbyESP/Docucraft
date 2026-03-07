package com.composepdf.renderer

import android.graphics.Bitmap
import android.util.Log
import android.util.Size
import com.composepdf.cache.BitmapCache
import com.composepdf.cache.BitmapPool
import com.composepdf.cache.PageCacheKey
import com.composepdf.cache.TileDiskCache
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
import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.PriorityBlockingQueue
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
    val cacheKey: String,
    val pageIndex: Int,
    val sessionToken: Int,
    val pageCacheKey: PageCacheKey? = null,
    val action: suspend () -> Unit
) : Comparable<RenderTask> {
    override fun compareTo(other: RenderTask): Int {
        val priorityCompare = this.priority.ordinal.compareTo(other.priority.ordinal)
        if (priorityCompare != 0) return priorityCompare
        // Within same priority, sort by distance to viewport center (Euclidean distance squared)
        return this.distanceSq.compareTo(other.distanceSq)
    }
}

/**
 * Orchestrates background rendering tasks for both full pages and high-resolution tiles.
 *
 * This scheduler uses a unified [PriorityBlockingQueue] and a fixed pool of 3 worker threads
 * to prevent thread starvation during heavy scroll/zoom operations.
 *
 * Everything is treated as a "tile" (base pages are just low-res tiles), allowing for
 * consistent priority management and memory pressure control.
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

    /** Number of pages to pre-render outside the visible range in each direction. */
    var prefetchWindow: Int = 2
        set(value) { field = value.coerceAtLeast(0) }

    /** Stable identifier for the currently open document. */
    var docKey: String = ""

    private val _renderedPages = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())

    /** Observed by the UI to display base page bitmaps. */
    val renderedPages: StateFlow<Map<Int, Bitmap>> = _renderedPages.asStateFlow()

    init {
        // Launch 3 workers to consume tasks from the priority queue infinitely.
        repeat(3) {
            scope.launch {
                while (isActive) {
                    try {
                        // Blocking take() - suspends worker until a task is available
                        val task = taskQueue.take()
                        executeTaskIfValid(task)
                    } catch (e: InterruptedException) {
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
     * Verifies if a task is still relevant before executing it.
     * Checks the session token and the "keep-set" (viewport window).
     */
    private suspend fun executeTaskIfValid(task: RenderTask) {
        val currentSession = renderWindowTracker.currentSessionToken()
        if (task.sessionToken != currentSession) return

        if (task.priority == RenderPriority.VISIBLE_TILE || task.priority == RenderPriority.PREFETCH_TILE) {
            // High-res tiles must still be in the current desired tile set
            if (!renderWindowTracker.shouldPublishTile(currentSession, task.cacheKey)) return
        } else {
            // Base pages must match the latest layout specification for that page
            val pck = task.pageCacheKey
            if (pck != null && !renderWindowTracker.shouldPublishPage(currentSession, task.pageIndex, pck)) return
        }

        task.action()
    }

    /**
     * Prevents the task queue from growing indefinitely during rapid user interaction.
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
    }

    /**
     * Replaces the current desired high-resolution tile window.
     */
    fun updateTileWindow(keepKeys: Set<String>) {
        renderWindowTracker.updateDesiredTiles(keepKeys)
    }

    /**
     * Schedules rendering for the given range of visible pages.
     * Pages outside [visiblePages] but within [prefetchWindow] are treated as PREFETCH_LOW_RES.
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
        val sessionToken = renderWindowTracker.currentSessionToken()
        val roundedZoom = (config.zoomLevel * 100f).roundToInt() / 100f
        val total = pageSizes.size

        val winStart = (visiblePages.first - prefetchWindow).coerceAtLeast(0)
        val winEnd = (visiblePages.last + prefetchWindow).coerceAtMost(total - 1)
        val window = winStart..winEnd
        
        // Clean up the published map - remove bitmaps for pages no longer in the active window
        retainRenderedPages(window.toSet())

        val desiredPages = linkedMapOf<Int, PageCacheKey>()
        window.forEach { pageIndex ->
            if (pageIndex !in 0 until total) return@forEach
            val pageSize = pageSizes[pageIndex]
            val baseWidth = getBaseWidth(pageIndex)
            val (targetW, targetH) = pageRenderer.calculateRenderSize(
                pageSize.width,
                pageSize.height,
                baseWidth,
                config
            )
            desiredPages[pageIndex] = PageCacheKey(pageIndex, roundedZoom, targetW, targetH)
        }
        renderWindowTracker.updateDesiredPages(desiredPages)

        for (pageIndex in window) {
            val cacheKey = desiredPages[pageIndex] ?: continue
            
            // Fast-path: if already in RAM cache, publish immediately
            val cached = cache.get(cacheKey)
            if (cached != null) {
                if (renderWindowTracker.shouldPublishPage(sessionToken, pageIndex, cacheKey)) {
                    publishBitmap(pageIndex, cached)
                }
                telemetry?.recordPageMemoryHit(renderPassId, pageIndex, config.zoomLevel)
                continue
            }

            val priority = if (pageIndex in visiblePages) RenderPriority.VISIBLE_LOW_RES else RenderPriority.PREFETCH_LOW_RES
            
            val task = RenderTask(
                priority = priority,
                distanceSq = abs(pageIndex - visiblePages.first).toFloat(),
                cacheKey = cacheKey.toString(),
                pageIndex = pageIndex,
                sessionToken = sessionToken,
                pageCacheKey = cacheKey,
                action = {
                    try {
                        val bitmap = documentManager.withPage(pageIndex) { page ->
                            pageRenderer.render(page, getBaseWidth(pageIndex), config)
                        }
                        cache.put(cacheKey, bitmap)
                        
                        val stale = !renderWindowTracker.shouldPublishPage(sessionToken, pageIndex, cacheKey)
                        if (!stale) {
                            publishBitmap(pageIndex, bitmap)
                        }
                        telemetry?.recordPagePublished(renderPassId, pageIndex, stale)
                    } catch (e: Exception) {
                        if (e !is CancellationException) Log.e(TAG, "Page error: ${e.message}")
                    }
                }
            )
            taskQueue.offer(task)
        }
    }

    /**
     * Schedules a single high-resolution tile render task.
     * Tiles are checked against [tileDiskCache] before rasterization.
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
        val diskKey = tileKey.toDiskCacheKey(docKey)

        // Skip if already in memory
        if (viewerState.getTile(memoryKey) != null) return

        val priority = if (isPrefetch) RenderPriority.PREFETCH_TILE else RenderPriority.VISIBLE_TILE

        val task = RenderTask(
            priority = priority,
            distanceSq = distanceSq,
            cacheKey = memoryKey,
            pageIndex = tileKey.pageIndex,
            sessionToken = sessionToken,
            action = {
                try {
                    // Try Disk Cache first
                    val diskBitmap = tileDiskCache?.get(diskKey)
                    if (diskBitmap != null) {
                        telemetry?.recordTileDiskHit(renderPassId, memoryKey)
                        if (renderWindowTracker.shouldPublishTile(sessionToken, memoryKey)) {
                            viewerState.putTile(memoryKey, diskBitmap)
                        } else {
                            bitmapPool.put(diskBitmap)
                        }
                    } else {
                        // Rasterize from PDF
                        val bitmap = documentManager.withPage(tileKey.pageIndex) { page ->
                            pageRenderer.renderTile(page, tileKey.rect, tileKey.zoom, baseWidth)
                        }
                        telemetry?.recordTileRendered(renderPassId, memoryKey)
                        tileDiskCache?.put(diskKey, bitmap)

                        val stale = !renderWindowTracker.shouldPublishTile(sessionToken, memoryKey)
                        if (!stale) {
                            viewerState.putTile(memoryKey, bitmap)
                        } else {
                            bitmapPool.put(bitmap)
                        }
                        telemetry?.recordTilePublished(renderPassId, memoryKey, stale)
                    }
                } catch (e: Exception) {
                    if (e !is CancellationException) Log.e(TAG, "Tile error: ${e.message}")
                }
            }
        )
        taskQueue.offer(task)
    }

    private fun publishBitmap(pageIndex: Int, bitmap: Bitmap) {
        _renderedPages.update { current ->
            if (current[pageIndex] === bitmap) current
            else current + (pageIndex to bitmap)
        }
    }

    private fun retainRenderedPages(pageIndices: Set<Int>) {
        _renderedPages.update { current ->
            val retained = current.filterKeys(pageIndices::contains)
            if (retained.size == current.size) current else retained
        }
    }

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

private fun abs(n: Int) = if (n < 0) -n else n
