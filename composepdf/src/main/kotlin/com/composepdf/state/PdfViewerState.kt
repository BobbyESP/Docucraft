package com.composepdf.state

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
import com.composepdf.state.PdfViewerState.Companion.Saver

/**
 * Hoistable state for the PDF viewer.
 *
 * ## Coordinate model
 *
 * The viewer uses a single 2-D transformation to map document positions to screen positions:
 *
 *   screenPos = docPos × zoom + Offset(panX, panY)
 *
 * All pages live in "document space" (zoom = 1, pan = 0). At zoom = 1 the first page
 * fills the viewport width exactly. [panY] scrolls the document vertically; [panX]
 * is only non-zero when zoomed in enough that the page is wider than the viewport.
 *
 * This single-transform model eliminates the dual-state problem that arises from
 * combining a [LazyListState] for scrolling with a separate [graphicsLayer] for zoom.
 *
 * ## State hoisting
 *
 * Create an instance with [rememberPdfViewerState] and pass it to [PdfViewer].
 * Reading [zoom], [panX], [panY], [currentPage] etc. from your own composables
 * is safe — all fields are Compose `State` objects that trigger recomposition.
 *
 * ## Persistence
 *
 * [Saver] saves [currentPage], [zoom], [panX], and [panY] across configuration
 * changes (rotation, font-size change, etc.) via [rememberSaveable].
 */
@Stable
class PdfViewerState(
    initialPage: Int = 0,
    initialZoom: Float = 1f
) {
    /** Zero-based index of the page most visible in the viewport. */
    var currentPage: Int by mutableIntStateOf(initialPage)
        internal set

    /** Total number of pages in the loaded document. */
    var pageCount: Int by mutableIntStateOf(0)
        internal set

    /**
     * Current zoom level (1f = fit-width).
     * Constrained to [ViewerConfig.minZoom]..[ViewerConfig.maxZoom].
     */
    var zoom: Float by mutableFloatStateOf(initialZoom)
        internal set

    /**
     * Horizontal translation of the document origin relative to the viewport
     * top-left, in screen pixels. Negative = document shifted left.
     */
    var panX: Float by mutableFloatStateOf(0f)
        internal set

    /**
     * Vertical translation of the document origin relative to the viewport
     * top-left, in screen pixels. Negative = document shifted up (scrolled down).
     */
    var panY: Float by mutableFloatStateOf(0f)
        internal set

    /** Whether the document is being loaded or rendered. */
    var isLoading: Boolean by mutableStateOf(true)
        internal set

    /** Last error that occurred, or null. */
    var error: Throwable? by mutableStateOf(null)
        internal set

    /** Whether a gesture is currently active (used to suppress re-renders mid-gesture). */
    var isGestureActive: Boolean by mutableStateOf(false)
        internal set

    /** Download/cache state for remote PDF sources. */
    var remoteState: RemotePdfState by mutableStateOf(RemotePdfState.Idle)
        internal set

    /** True when a document has been loaded successfully. */
    val isLoaded: Boolean
        get() = !isLoading && error == null && pageCount > 0

    /**
     * Convenience alias kept for callsites that used the old [offset] property.
     * Maps to [Offset(panX, panY)].
     */
    val offset: Offset get() = Offset(panX, panY)

    internal fun reset() {
        currentPage = 0
        pageCount = 0
        zoom = 1f
        panX = 0f
        panY = 0f
        isLoading = true
        error = null
        isGestureActive = false
    }

    companion object {
        val Saver: Saver<PdfViewerState, *> = listSaver(
            save = { listOf(it.currentPage, it.zoom, it.panX, it.panY) },
            restore = {
                PdfViewerState(initialPage = it[0] as Int, initialZoom = it[1] as Float).also { s ->
                    s.panX = it[2] as Float
                    s.panY = it[3] as Float
                }
            }
        )
    }
}
