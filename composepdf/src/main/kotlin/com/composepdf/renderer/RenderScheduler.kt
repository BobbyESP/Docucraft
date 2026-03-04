package com.composepdf.renderer

import android.graphics.Bitmap
import android.util.Log
import android.util.Size
import com.composepdf.cache.BitmapCache
import com.composepdf.cache.PageCacheKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import kotlin.math.roundToInt

private const val TAG = "PdfRenderScheduler"

/**
 * Orchestrates background rendering of PDF pages with safe bitmap management.
 */
class RenderScheduler(
    private val documentManager: PdfDocumentManager,
    private val pageRenderer: PageRenderer,
    private val cache: BitmapCache
) : Closeable {

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val activeJobs = HashMap<Int, Job>()
    private val inFlightZoom = HashMap<Int, Float>()

    var prefetchWindow: Int = 2
        set(value) { field = value.coerceAtLeast(0) }

    private val _renderedPages = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())
    val renderedPages: StateFlow<Map<Int, Bitmap>> = _renderedPages.asStateFlow()

    /**
     * Set of bitmaps that were once published but are now replaced.
     * We keep track of them to return them to the pool only when safe.
     */
    private val retiredBitmaps = mutableSetOf<Bitmap>()

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

        // Cancel jobs for pages that moved out of the window
        val iter = activeJobs.iterator()
        while (iter.hasNext()) {
            val (idx, job) = iter.next()
            if (idx !in window) {
                job.cancel()
                inFlightZoom.remove(idx)
                iter.remove()
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
                    val bitmap = withContext(Dispatchers.IO) {
                        documentManager.withPage(pageIndex) { page ->
                            pageRenderer.render(page, config)
                        }
                    }

                    // On Main thread:
                    cache.put(cacheKey, bitmap)
                    
                    // Only remove from tracking if this job is still the current one
                    if (inFlightZoom[pageIndex] == roundedZoom) {
                        inFlightZoom.remove(pageIndex)
                        activeJobs.remove(pageIndex)
                        publishBitmap(pageIndex, bitmap)
                    } else {
                        // This render is stale (zoom changed again), return to cache/pool logic
                        // will be handled by the next job or cache eviction
                    }

                } catch (_: CancellationException) {
                    inFlightZoom.remove(pageIndex)
                    activeJobs.remove(pageIndex)
                } catch (e: Exception) {
                    Log.e(TAG, "Render error for page $pageIndex: ${e.message}")
                    inFlightZoom.remove(pageIndex)
                    activeJobs.remove(pageIndex)
                }
            }
        }
    }

    private fun publishBitmap(pageIndex: Int, bitmap: Bitmap) {
        _renderedPages.update { current ->
            val old = current[pageIndex]
            if (old === bitmap) return@update current
            
            // If we are replacing a bitmap, it's a candidate for the pool.
            // But we can only pool it if it's NOT in the LRU cache either.
            // We'll let the Cache decide when it's truly evicted from memory.
            current + (pageIndex to bitmap)
        }
    }

    fun invalidateAll() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        inFlightZoom.clear()
        // We clear the cache, which will trigger BitmapCache.entryRemoved.
        // We MUST ensure entryRemoved only pools bitmaps not in _renderedPages.
        cache.clear()
    }

    override fun close() {
        scope.cancel()
        activeJobs.clear()
        inFlightZoom.clear()
        cache.clear()
    }

    private fun roundZoom(zoom: Float): Float = (zoom * 100f).roundToInt() / 100f
}
