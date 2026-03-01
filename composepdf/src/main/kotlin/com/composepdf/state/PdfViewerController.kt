package com.composepdf.state

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import com.composepdf.cache.BitmapCache
import com.composepdf.cache.BitmapPool
import com.composepdf.renderer.PageRenderer
import com.composepdf.renderer.PdfDocumentManager
import com.composepdf.renderer.RenderScheduler
import com.composepdf.source.PdfSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.Closeable
import kotlin.math.abs

/**
 * Controller for PDF viewer operations.
 * 
 * This class manages the internal workings of the PDF viewer, including document
 * loading, rendering coordination, and state updates. It bridges the public
 * [PdfViewerState] with the internal rendering infrastructure.
 * 
 * The controller should be created once per viewer instance and closed when
 * the viewer is disposed.
 * 
 * @property context Android context for resource access
 * @property state The viewer state to update
 * @property config The viewer configuration
 */
class PdfViewerController(
    private val context: Context,
    private val state: PdfViewerState,
    private val config: ViewerConfig
) : Closeable {
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Memory management
    private val bitmapPool = BitmapPool()
    private val bitmapCache = BitmapCache(bitmapPool = bitmapPool)
    
    // Rendering infrastructure
    private val documentManager = PdfDocumentManager(context)
    private val pageRenderer = PageRenderer(bitmapPool)
    private val renderScheduler = RenderScheduler(documentManager, pageRenderer, bitmapCache)
    
    // Track last zoom for invalidation
    private var lastRenderedZoom = 1f
    
    /**
     * Flow of rendered page bitmaps, keyed by page index.
     */
    val renderedPages: StateFlow<Map<Int, Bitmap>> = renderScheduler.renderedPages
    
    init {
        renderScheduler.prefetchWindow = config.prefetchDistance
    }
    
    /**
     * Loads a PDF document from the given source.
     * 
     * This operation is asynchronous and updates [PdfViewerState.isLoading]
     * and [PdfViewerState.error] accordingly.
     * 
     * For remote sources, also updates [PdfViewerState.remoteState] with
     * download progress.
     * 
     * @param source The PDF source to load
     */
    fun loadDocument(source: PdfSource) {
        scope.launch {
            state.reset()
            state.isLoading = true
            state.error = null
            
            try {
                when (source) {
                    is PdfSource.Remote -> {
                        // Handle remote source with progress tracking
                        loadRemoteDocument(source)
                    }
                    else -> {
                        // Handle local sources directly
                        documentManager.open(source)
                        state.pageCount = documentManager.pageCount
                        state.isLoading = false
                        
                        // Initial render
                        requestRenderForVisiblePages()
                    }
                }
            } catch (e: Exception) {
                state.error = e
                state.isLoading = false
            }
        }
    }
    
    /**
     * Loads a remote PDF document with progress tracking.
     */
    private suspend fun loadRemoteDocument(source: PdfSource.Remote) {
        val loader = com.composepdf.remote.RemotePdfLoader(context)
        
        loader.load(source).collect { remoteState ->
            state.remoteState = remoteState
            
            when (remoteState) {
                is com.composepdf.remote.RemotePdfState.Cached -> {
                    // File is ready, open it
                    documentManager.open(PdfSource.FromFile(remoteState.file))
                    state.pageCount = documentManager.pageCount
                    state.isLoading = false
                    
                    // Initial render
                    requestRenderForVisiblePages()
                }
                is com.composepdf.remote.RemotePdfState.Error -> {
                    state.error = com.composepdf.remote.RemotePdfException(
                        remoteState.type,
                        remoteState.message,
                        remoteState.cause
                    )
                    state.isLoading = false
                }
                else -> {
                    // Downloading or Idle - keep loading state
                }
            }
        }
    }
    
    /**
     * Navigates to a specific page.
     * 
     * @param pageIndex The zero-based page index
     * @param animate Whether to animate the scroll
     */
    fun goToPage(pageIndex: Int, animate: Boolean = true) {
        val validIndex = pageIndex.coerceIn(0, state.pageCount - 1)
        
        scope.launch {
            if (animate) {
                state.lazyListState.animateScrollToItem(validIndex)
            } else {
                state.lazyListState.scrollToItem(validIndex)
            }
            state.currentPage = validIndex
        }
    }
    
    /**
     * Zooms to a specific level, optionally centered on a pivot point.
     * 
     * @param zoom The target zoom level
     * @param pivot The point to zoom around (screen coordinates)
     * @param animate Whether to animate the zoom change
     */
    fun zoomTo(zoom: Float, pivot: Offset = Offset.Zero, animate: Boolean = true) {
        val clampedZoom = zoom.coerceIn(config.minZoom, config.maxZoom)
        
        if (pivot != Offset.Zero) {
            // Adjust offset to zoom around the pivot point
            val scaleFactor = clampedZoom / state.zoom
            val pivotOffset = pivot - state.offset
            state.offset = pivot - (pivotOffset * scaleFactor)
        }
        
        state.zoom = clampedZoom
        
        // Check if we need to invalidate renders due to significant zoom change
        checkZoomInvalidation()
    }
    
    /**
     * Updates the zoom and offset from a gesture.
     * 
     * @param zoomChange Multiplicative zoom change
     * @param panChange Additive pan change
     * @param pivot The center point of the gesture
     */
    fun onGestureUpdate(zoomChange: Float, panChange: Offset, pivot: Offset) {
        val newZoom = (state.zoom * zoomChange).coerceIn(config.minZoom, config.maxZoom)
        
        if (newZoom != state.zoom) {
            // Calculate new offset to maintain pivot position
            val scaleFactor = newZoom / state.zoom
            val pivotOffset = pivot - state.offset
            state.offset = pivot - (pivotOffset * scaleFactor)
            state.zoom = newZoom
        }
        
        state.offset += panChange
    }
    
    /**
     * Called when a gesture starts.
     */
    fun onGestureStart() {
        state.isGestureActive = true
    }
    
    /**
     * Called when a gesture ends.
     */
    fun onGestureEnd() {
        state.isGestureActive = false
        clampOffset()
        checkZoomInvalidation()
    }
    
    /**
     * Toggles between 1x zoom and the configured double-tap zoom level.
     * 
     * @param pivot The point to center the zoom on
     */
    fun toggleDoubleTapZoom(pivot: Offset) {
        val targetZoom = if (state.zoom < config.doubleTapZoom * 0.9f) {
            config.doubleTapZoom
        } else {
            1f
        }
        
        zoomTo(targetZoom, pivot)
    }
    
    /**
     * Updates the current page based on scroll position.
     * 
     * @param firstVisibleItemIndex The first visible item index from LazyListState
     */
    fun onScrollPositionChanged(firstVisibleItemIndex: Int) {
        if (state.currentPage != firstVisibleItemIndex) {
            state.currentPage = firstVisibleItemIndex
        }
    }
    
    /**
     * Requests rendering of currently visible pages and those in the prefetch window.
     */
    fun requestRenderForVisiblePages() {
        if (!documentManager.isOpen) return
        
        scope.launch {
            val firstVisible = state.lazyListState.firstVisibleItemIndex
            val lastVisible = state.lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                ?: firstVisible
            
            val renderConfig = PageRenderer.RenderConfig(
                zoomLevel = state.zoom,
                renderQuality = config.renderQuality,
                nightMode = config.isNightModeEnabled
            )
            
            renderScheduler.requestRender(
                visiblePages = firstVisible..lastVisible,
                config = renderConfig
            )
            
            lastRenderedZoom = state.zoom
        }
    }
    
    private fun clampOffset() {
        // Clamp offset to prevent panning beyond content bounds
        // This would need content size information in a full implementation
        // For now, just ensure offset doesn't go too far
        if (state.zoom <= 1f) {
            state.offset = Offset.Zero
        }
    }
    
    private fun checkZoomInvalidation() {
        val zoomDifference = abs(state.zoom - lastRenderedZoom)
        if (zoomDifference > ZOOM_INVALIDATION_THRESHOLD) {
            scope.launch {
                renderScheduler.invalidateAll()
                requestRenderForVisiblePages()
            }
        }
    }
    
    /**
     * Closes the controller and releases all resources.
     */
    override fun close() {
        scope.cancel()
        renderScheduler.close()
        documentManager.close()
        bitmapCache.clear()
        bitmapPool.clear()
    }
    
    companion object {
        private const val ZOOM_INVALIDATION_THRESHOLD = 0.1f
    }
}
