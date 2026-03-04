package com.composepdf.renderer

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import android.util.Size
import com.composepdf.cache.BitmapCache
import com.composepdf.cache.PageCacheKey
import com.composepdf.state.PdfViewerState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.roundToInt

private const val TAG = "PdfRenderScheduler"

/**
 * Orchestrates background rendering tasks for both full pages and high-resolution tiles.
 * 
 * It uses a dedicated thread pool to ensure rendering operations don't block the 
 * main thread or standard IO dispatchers.
 */
class RenderScheduler(
    private val documentManager: PdfDocumentManager,
    private val pageRenderer: PageRenderer,
    private val cache: BitmapCache,
    private val viewerState: PdfViewerState
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

    private val _renderedPages = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())
    /** Observed by the UI to display base page bitmaps. */
    val renderedPages: StateFlow<Map<Int, Bitmap>> = _renderedPages.asStateFlow()

    /**
     * Schedules rendering for the given range of visible pages.
     * Manages prefetching and cancels out-of-window jobs.
     */
    fun requestRender(
        visiblePages: IntRange,
        config: PageRenderer.RenderConfig,
        pageSizes: List<Size>
    ) {
        if (!documentManager.isOpen || pageSizes.isEmpty()) return

        val roundedZoom = (config.zoomLevel * 100f).roundToInt() / 100f
        val total = pageSizes.size

        val winStart = (visiblePages.first - prefetchWindow).coerceAtLeast(0)
        val winEnd   = (visiblePages.last  + prefetchWindow).coerceAtMost(total - 1)
        val window   = winStart..winEnd

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
            val (targetW, targetH) = pageRenderer.calculateRenderSize(
                pageSize.width, pageSize.height, config
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

            if (activeJobs[pageIndex]?.isActive == true && inFlightZoom[pageIndex] == roundedZoom) {
                continue
            }

            activeJobs[pageIndex]?.cancel()
            inFlightZoom[pageIndex] = roundedZoom

            activeJobs[pageIndex] = scope.launch {
                try {
                    val bitmap = documentManager.withPage(pageIndex) { page ->
                        pageRenderer.render(page, config)
                    }
                    
                    cache.put(cacheKey, bitmap)
                    if (inFlightZoom[pageIndex] == roundedZoom) {
                        inFlightZoom.remove(pageIndex)
                        activeJobs.remove(pageIndex)
                        publishBitmap(pageIndex, bitmap)
                    }
                } catch (e: Exception) {
                    if (e !is CancellationException) Log.e(TAG, "Render error for page $pageIndex: ${e.message}")
                    inFlightZoom.remove(pageIndex)
                    activeJobs.remove(pageIndex)
                }
            }
        }
    }

    /**
     * Schedules a single tile render.
     */
    fun requestTile(
        pageIndex: Int,
        tileRect: Rect,
        zoom: Float,
        viewportWidth: Float
    ) {
        val zoomKey = (zoom * 100f).roundToInt() / 100f
        val tileKey = "${pageIndex}_${tileRect.left}_${tileRect.top}_${tileRect.right}_${tileRect.bottom}_$zoomKey"
        
        if (tileJobs.containsKey(tileKey)) return

        tileJobs[tileKey] = scope.launch {
            try {
                val bitmap = documentManager.withPage(pageIndex) { page ->
                    pageRenderer.renderTile(page, tileRect, zoom, viewportWidth)
                }
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
        activeJobs.values.forEach { it.cancel() }
        tileJobs.values.forEach { it.cancel() }
        renderDispatcher.close()
    }
}
