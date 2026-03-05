package com.composepdf.renderer

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import android.util.Size
import com.composepdf.cache.BitmapCache
import com.composepdf.cache.PageCacheKey
import com.composepdf.cache.TileDiskCache
import com.composepdf.state.PdfViewerState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.roundToInt

private const val TAG = "PdfRenderScheduler"

/**
 * Orchestrates background rendering tasks for both full pages and high-resolution tiles.
 *
 * Uses a dedicated thread pool to ensure rendering operations don't block the
 * main thread or standard IO dispatchers.
 */
class RenderScheduler(
    private val documentManager: PdfDocumentManager,
    private val pageRenderer: PageRenderer,
    private val cache: BitmapCache,
    private val viewerState: PdfViewerState,
    private val tileDiskCache: TileDiskCache? = null
) : Closeable {

    /** Dedicated dispatcher for CPU-intensive PDF rasterization. */
    private val renderDispatcher = Executors.newFixedThreadPool(4).asCoroutineDispatcher()
    private val scope = CoroutineScope(renderDispatcher + SupervisorJob())

    /** Tracking of active page-level rendering jobs. */
    private val activeJobs = ConcurrentHashMap<Int, Job>()
    private val inFlightZoom = ConcurrentHashMap<Int, Float>()

    /** Tracking of active high-res tile rendering jobs. */
    private val tileJobs = ConcurrentHashMap<String, Job>()

    /** Number of pages to pre-render outside the visible range. */
    var prefetchWindow: Int = 2
        set(value) { field = value.coerceAtLeast(0) }

    /** Current document identifier, set by [com.composepdf.state.PdfViewerController] on each open(). */
    var docKey: String = ""

    private val _renderedPages = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())

    /** Observed by the UI to display base page bitmaps. */
    val renderedPages: StateFlow<Map<Int, Bitmap>> = _renderedPages.asStateFlow()

    /**
     * Schedules rendering for the given range of visible pages.
     *
     * @param visiblePages Range of indices currently visible in the viewport.
     * @param config Quality settings for the render.
     * @param pageSizes Original PDF dimensions for all pages.
     * @param getBaseWidth A function to retrieve the base width (at zoom 1.0) for a specific page index.
     */
    fun requestRender(
        visiblePages: IntRange,
        config: PageRenderer.RenderConfig,
        pageSizes: List<Size>,
        getBaseWidth: (Int) -> Float
    ) {
        if (!documentManager.isOpen || pageSizes.isEmpty()) return

        val roundedZoom = (config.zoomLevel * 100f).roundToInt() / 100f
        val total = pageSizes.size

        val winStart = (visiblePages.first - prefetchWindow).coerceAtLeast(0)
        val winEnd = (visiblePages.last + prefetchWindow).coerceAtMost(total - 1)
        val window = winStart..winEnd

        // Evict jobs for pages that are no longer in the prefetch window
        activeJobs.keys.forEach { idx ->
            if (idx !in window) {
                activeJobs.remove(idx)?.cancel()
                inFlightZoom.remove(idx)
            }
        }

        // Prioritize visible pages, then prefetched ones
        val ordered = visiblePages.toList() +
                (winStart until visiblePages.first).toList() +
                ((visiblePages.last + 1)..winEnd).toList()

        for (pageIndex in ordered) {
            if (pageIndex !in 0 until total) continue

            val pageSize = pageSizes[pageIndex]
            val baseWidth = getBaseWidth(pageIndex)
            val (targetW, targetH) = pageRenderer.calculateRenderSize(
                pageSize.width,
                pageSize.height,
                baseWidth,
                config
            )
            val cacheKey = PageCacheKey(pageIndex, roundedZoom, targetW, targetH)

            // Skip if already in memory or already being rendered at this zoom
            val cached = cache.get(cacheKey)
            if (cached != null) {
                publishBitmap(pageIndex, cached)
                activeJobs.remove(pageIndex)?.cancel()
                inFlightZoom.remove(pageIndex)
                continue
            }

            if (activeJobs[pageIndex]?.isActive == true && inFlightZoom[pageIndex] == roundedZoom) continue

            activeJobs[pageIndex]?.cancel()
            inFlightZoom[pageIndex] = roundedZoom

            activeJobs[pageIndex] = scope.launch {
                try {
                    val bitmap = documentManager.withPage(pageIndex) { page ->
                        pageRenderer.render(page, baseWidth, config)
                    }
                    cache.put(cacheKey, bitmap)
                    if (inFlightZoom[pageIndex] == roundedZoom) {
                        inFlightZoom.remove(pageIndex)
                        activeJobs.remove(pageIndex)
                        publishBitmap(pageIndex, bitmap)
                    }
                } catch (e: Exception) {
                    if (e !is CancellationException) Log.e(
                        TAG,
                        "Render error for page $pageIndex: ${e.message}"
                    )
                    inFlightZoom.remove(pageIndex)
                    activeJobs.remove(pageIndex)
                }
            }
        }
    }

    /**
     * Schedules a single tile render task.
     *
     * @param pageIndex The index of the page containing the tile.
     * @param tileRect The coordinates of the tile within the page (scaled coordinates).
     * @param zoom The current zoom level.
     * @param baseWidth The width the page should have at zoom 1.0.
     */
    fun requestTile(
        pageIndex: Int,
        tileRect: Rect,
        zoom: Float,
        baseWidth: Float
    ) {
        val zoomKey = (zoom * 100f).roundToInt() / 100f
        // Memory key: original format, used by PdfViewerState and PdfPage for drawing.
        val tileKey = "${pageIndex}_${tileRect.left}_${tileRect.top}_${tileRect.right}_${tileRect.bottom}_$zoomKey"
        // Disk key: prefixed with docKey to avoid collisions between different PDFs on disk.
        val diskKey = if (docKey.isNotEmpty()) "${docKey}_$tileKey" else tileKey

        // Already in memory cache
        if (viewerState.getTile(tileKey) != null) return
        // Already being rendered
        if (tileJobs.containsKey(tileKey)) return

        tileJobs[tileKey] = scope.launch {
            try {
                val diskBitmap = tileDiskCache?.get(diskKey)
                if (diskBitmap != null) {
                    viewerState.putTile(tileKey, diskBitmap)
                    return@launch
                }

                val bitmap = documentManager.withPage(pageIndex) { page ->
                    pageRenderer.renderTile(page, tileRect, zoom, baseWidth)
                }
                tileDiskCache?.put(diskKey, bitmap)
                viewerState.putTile(tileKey, bitmap)
            } catch (e: Exception) {
                if (e !is CancellationException) Log.e(TAG, "Tile error for $tileKey: ${e.message}")
            } finally {
                tileJobs.remove(tileKey)
            }
        }
    }

    /**
     * Cancels all tile rendering jobs that are not present in the [keepKeys] set.
     * Essential for maintaining performance during fast scrolls.
     */
    fun pruneTileJobs(keepKeys: Set<String>) {
        tileJobs.keys.forEach { key ->
            if (key !in keepKeys) {
                tileJobs.remove(key)?.cancel()
            }
        }
    }

    /** Cancels all currently active tile rendering tasks. */
    fun cancelAllTiles() {
        tileJobs.keys.forEach { key ->
            tileJobs.remove(key)?.cancel()
        }
    }

    private fun publishBitmap(pageIndex: Int, bitmap: Bitmap) {
        _renderedPages.update { current ->
            if (current[pageIndex] === bitmap) current
            else current + (pageIndex to bitmap)
        }
    }

    /** Cancels everything and wipes the page cache. */
    fun invalidateAll() {
        activeJobs.keys.forEach { idx -> activeJobs.remove(idx)?.cancel() }
        inFlightZoom.clear()
        cancelAllTiles()
        cache.clear()
    }

    override fun close() {
        invalidateAll()
        scope.cancel()
        renderDispatcher.close()
    }
}
