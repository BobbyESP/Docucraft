package com.composepdf.renderer

import android.graphics.Bitmap
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable

/**
 * Coordinates background page rendering with priority management and cancellation support.
 * 
 * The scheduler manages render jobs for visible and prefetched pages, ensuring that:
 * - Visible pages are rendered with highest priority
 * - Adjacent pages are prefetched in the background
 * - Stale render jobs are cancelled when scroll/zoom changes
 * - Rendered bitmaps are cached for quick access
 * 
 * @property documentManager The PDF document manager
 * @property pageRenderer The page renderer
 * @property cache The bitmap cache
 */
class RenderScheduler(
    private val documentManager: PdfDocumentManager,
    private val pageRenderer: PageRenderer,
    private val cache: BitmapCache
) : Closeable {
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val pendingJobs = mutableMapOf<Int, Job>()
    private val jobMutex = Mutex()
    
    /**
     * Map of page index to rendered bitmap, exposed as a StateFlow for UI observation.
     */
    private val _renderedPages = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())
    val renderedPages: StateFlow<Map<Int, Bitmap>> = _renderedPages.asStateFlow()
    
    /**
     * The prefetch window size (number of pages to prefetch on each side of visible pages).
     */
    var prefetchWindow: Int = 2
        set(value) {
            field = value.coerceAtLeast(0)
        }
    
    /**
     * Requests rendering of pages within the visible range plus prefetch window.
     * 
     * Pages that are already cached or being rendered are skipped.
     * Pages outside the new range have their pending render jobs cancelled.
     * 
     * @param visiblePages The range of currently visible page indices
     * @param config The rendering configuration
     */
    suspend fun requestRender(
        visiblePages: IntRange,
        config: PageRenderer.RenderConfig
    ) = jobMutex.withLock {
        if (!documentManager.isOpen) return@withLock
        
        val pageCount = documentManager.pageCount
        val startPage = (visiblePages.first - prefetchWindow).coerceAtLeast(0)
        val endPage = (visiblePages.last + prefetchWindow).coerceAtMost(pageCount - 1)
        
        val pagesToRender = startPage..endPage
        
        // Cancel jobs for pages no longer in range
        pendingJobs.entries.toList().forEach { (pageIndex, job) ->
            if (pageIndex !in pagesToRender) {
                job.cancel()
                pendingJobs.remove(pageIndex)
            }
        }
        
        // Schedule rendering for pages in range
        for (pageIndex in pagesToRender) {
            // Check cache first
            val pageSize = documentManager.getPageSize(pageIndex)
            val (width, height) = pageRenderer.calculateRenderSize(
                pageSize.width,
                pageSize.height,
                config
            )
            val cacheKey = PageCacheKey(pageIndex, config.zoomLevel, width, height)
            
            val cachedBitmap = cache.get(cacheKey)
            if (cachedBitmap != null) {
                updateRenderedPages(pageIndex, cachedBitmap)
                continue
            }
            
            // Skip if already being rendered
            if (pendingJobs[pageIndex]?.isActive == true) {
                continue
            }
            
            // Schedule render job
            val job = scope.launch {
                try {
                    val bitmap = documentManager.withPage(pageIndex) { page ->
                        pageRenderer.render(page, config)
                    }
                    
                    cache.put(cacheKey, bitmap)
                    updateRenderedPages(pageIndex, bitmap)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Log error but don't crash
                    e.printStackTrace()
                }
            }
            
            pendingJobs[pageIndex] = job
        }
    }
    
    /**
     * Invalidates cached renders for all pages.
     * 
     * Call this when zoom level changes significantly to trigger re-rendering
     * at the new resolution.
     */
    suspend fun invalidateAll() = jobMutex.withLock {
        // Cancel all pending jobs
        pendingJobs.values.forEach { it.cancel() }
        pendingJobs.clear()
        
        // Clear cache
        cache.clear()
        
        // Clear rendered pages
        _renderedPages.value = emptyMap()
    }
    
    /**
     * Invalidates a specific page, removing it from cache and cancelling any pending render.
     * 
     * @param pageIndex The page index to invalidate
     */
    suspend fun invalidatePage(pageIndex: Int) = jobMutex.withLock {
        pendingJobs[pageIndex]?.cancel()
        pendingJobs.remove(pageIndex)
        cache.clearPage(pageIndex)
        
        _renderedPages.value = _renderedPages.value - pageIndex
    }
    
    private fun updateRenderedPages(pageIndex: Int, bitmap: Bitmap) {
        _renderedPages.value = _renderedPages.value + (pageIndex to bitmap)
    }
    
    /**
     * Cancels all pending render jobs and clears state.
     */
    override fun close() {
        pendingJobs.values.forEach { it.cancel() }
        pendingJobs.clear()
    }
}
