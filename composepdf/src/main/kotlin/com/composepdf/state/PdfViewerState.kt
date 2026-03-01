package com.composepdf.state

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.composepdf.remote.RemotePdfState

/**
 * Hoistable state holder for the PDF viewer.
 * 
 * This class holds all observable state for a PDF viewer instance, including
 * current page, zoom level, scroll position, and loading state. It is designed
 * to be used with [com.composepdf.rememberPdfViewerState] and supports state restoration across
 * configuration changes.
 * 
 * The state is [Stable], meaning changes to individual properties will trigger
 * minimal recomposition in Compose.
 * 
 * Example usage:
 * ```kotlin
 * @Composable
 * fun MyScreen() {
 *     val state = rememberPdfViewerState()
 *     
 *     PdfViewer(
 *         source = PdfSource.FromAsset("document.pdf"),
 *         state = state
 *     )
 *     
 *     // Access state
 *     Text("Page ${state.currentPage + 1} of ${state.pageCount}")
 * }
 * ```
 * 
 * @param initialPage The initial page to display (zero-based)
 * @param initialZoom The initial zoom level (1.0 = 100%)
 */
@Stable
class PdfViewerState(
    initialPage: Int = 0,
    initialZoom: Float = 1f
) {
    /**
     * The currently visible page index (zero-based).
     */
    var currentPage: Int by mutableIntStateOf(initialPage)
        internal set
    
    /**
     * The total number of pages in the document.
     */
    var pageCount: Int by mutableIntStateOf(0)
        internal set
    
    /**
     * The current zoom level (1.0 = 100%).
     * Constrained by [ViewerConfig.minZoom] and [ViewerConfig.maxZoom].
     */
    var zoom: Float by mutableFloatStateOf(initialZoom)
        internal set
    
    /**
     * The current pan offset when zoomed in.
     * This offset is relative to the top-left corner of the content.
     */
    var offset: Offset by mutableStateOf(Offset.Zero)
        internal set
    
    /**
     * Whether the document is currently being loaded.
     */
    var isLoading: Boolean by mutableStateOf(true)
        internal set
    
    /**
     * The last error that occurred, or null if no error.
     */
    var error: Throwable? by mutableStateOf(null)
        internal set
    
    /**
     * Whether a gesture (pinch/pan) is currently in progress.
     */
    var isGestureActive: Boolean by mutableStateOf(false)
        internal set
    
    /**
     * State of remote PDF loading (for URL sources).
     * 
     * This property is only relevant when loading from [com.composepdf.source.PdfSource.Remote].
     * For local sources, this will remain [com.composepdf.remote.RemotePdfState.Idle].
     * 
     * Observe this to show download progress or handle remote loading errors.
     */
    var remoteState: RemotePdfState by mutableStateOf(
        RemotePdfState.Idle
    )
        internal set
    
    /**
     * Internal scroll state for the lazy list.
     */
    internal val lazyListState: LazyListState = LazyListState(
        firstVisibleItemIndex = initialPage
    )
    
    /**
     * The first visible page index based on scroll position.
     * This may differ from [currentPage] during rapid scrolling.
     */
    val firstVisiblePage: Int
        get() = lazyListState.firstVisibleItemIndex
    
    /**
     * Whether the document has been loaded successfully.
     */
    val isLoaded: Boolean
        get() = !isLoading && error == null && pageCount > 0
    
    /**
     * Resets the state to initial values.
     */
    internal fun reset() {
        currentPage = 0
        pageCount = 0
        zoom = 1f
        offset = Offset.Zero
        isLoading = true
        error = null
    }
    
    companion object {
        /**
         * Saver for persisting [PdfViewerState] across configuration changes.
         * 
         * Note: Only page and zoom are persisted. Loading state and errors
         * are not persisted as they are transient.
         */
        val Saver: Saver<PdfViewerState, *> = listSaver(
            save = { state ->
                listOf(
                    state.currentPage,
                    state.zoom
                )
            },
            restore = { saved ->
                PdfViewerState(
                    initialPage = saved[0] as Int,
                    initialZoom = saved[1] as Float
                )
            }
        )
    }
}
