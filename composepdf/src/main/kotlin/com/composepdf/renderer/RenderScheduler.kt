package com.composepdf.renderer

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import android.util.Size
import com.composepdf.cache.BitmapCache
import com.composepdf.cache.PageCacheKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

private const val TAG = "PdfRenderScheduler"

class RenderScheduler(
    private val documentManager: PdfDocumentManager,
    private val pageRenderer: PageRenderer,
    private val cache: BitmapCache
) : Closeable {

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    
    // Concurrent map for active page jobs
    private val activeJobs = ConcurrentHashMap<Int, Job>()
    private val inFlightZoom = ConcurrentHashMap<Int, Float>()

    // Tile jobs: Key is the unique tile identifier
    private val tileJobs = ConcurrentHashMap<String, Job>()

    var prefetchWindow: Int = 2
        set(value) { field = value.coerceAtLeast(0) }

    private val _renderedPages = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())
    val renderedPages: StateFlow<Map<Int, Bitmap>> = _renderedPages.asStateFlow()

    fun requestRender(
        visiblePages: IntRange,
        config: PageRenderer.RenderConfig,
        pageSizes: List<Size>
    ) {
        if (!documentManager.isOpen || pageSizes.isEmpty()) return

        val roundedZoom = roundZoom(config.zoomLevel)
        val total = pageSizes.size

        val winStart = (visiblePages.first - prefetchWindow).coerceAtLeast(0)
        val winEnd   = (visiblePages.last  + prefetchWindow).coerceAtMost(total - 1)
        val window   = winStart..winEnd

        // Cancel jobs for pages out of window
        activeJobs.keys.forEach { idx ->
            if (idx !in window) {
                activeJobs.remove(idx)?.cancel()
                inFlightZoom.remove(idx)
            }
        }

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
                    if (e !is CancellationException) Log.e(TAG, "Render error: ${e.message}")
                    inFlightZoom.remove(pageIndex)
                    activeJobs.remove(pageIndex)
                }
            }
        }
    }

    fun requestTile(
        pageIndex: Int,
        tileRect: Rect,
        zoom: Float,
        viewportWidth: Float,
        onTileDone: (Bitmap) -> Unit
    ) {
        val tileKey = "${pageIndex}_${tileRect.left}_${tileRect.top}_${tileRect.right}_${tileRect.bottom}_$zoom"
        
        if (tileJobs.containsKey(tileKey)) return

        // Launch tile render in parallel
        tileJobs[tileKey] = scope.launch {
            try {
                val bitmap = documentManager.withPage(pageIndex) { page ->
                    pageRenderer.renderTile(page, tileRect, zoom, viewportWidth)
                }
                onTileDone(bitmap)
                tileJobs.remove(tileKey)
            } catch (e: Exception) {
                if (e !is CancellationException) Log.e(TAG, "Tile error: ${e.message}")
                tileJobs.remove(tileKey)
            }
        }
    }

    fun cancelAllTiles() {
        tileJobs.values.forEach { it.cancel() }
        tileJobs.clear()
    }

    private fun publishBitmap(pageIndex: Int, bitmap: Bitmap) {
        _renderedPages.update { current ->
            if (current[pageIndex] === bitmap) current
            else current + (pageIndex to bitmap)
        }
    }

    fun invalidateAll() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        inFlightZoom.clear()
        cancelAllTiles()
        cache.clear()
    }

    override fun close() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        tileJobs.values.forEach { it.cancel() }
        tileJobs.clear()
        scope.launch { cache.clear() }
    }

    private fun roundZoom(zoom: Float): Float = (zoom * 100f).roundToInt() / 100f
}
