package com.composepdf.internal.logic

import android.util.Size
import androidx.compose.ui.geometry.Offset
import com.composepdf.ViewerConfig

/**
 * Read-only contract providing access to the current layout and viewport state of the PDF viewer.
 *
 * This interface allows UI components and gesture handlers to query the dimensions of the
 * viewport, the positions of pages within the document coordinate space, and calculate
 */
internal interface ViewerViewportReadContract {
    val viewportWidth: Float
    val viewportHeight: Float
    val pageSizes: List<Size>

    fun pageHeightPx(index: Int): Float
    fun pageWidthPx(index: Int): Float
    fun pageTopDocY(index: Int): Float
    fun pageLeftDocX(index: Int): Float
    fun corridorBreadth(): Float
    fun visiblePageIndices(): IntRange
    fun isPointOverPage(point: Offset): Boolean
    fun computeCenteredPanForPage(pageIndex: Int): Pair<Float, Float>
    fun computeFitDocumentZoom(): Float
    fun computeFitPageZoom(pageIndex: Int): Float
}

/**
 * Interface for requesting document rendering updates.
 * Serves as a shared entry point for UI event callbacks and imperative state APIs
 * to trigger the rendering of currently visible pages.
 */
internal interface ViewerRenderRequester {
    fun requestRenderForVisiblePages()
}

/**
 * Defines viewport geometry mutations triggered by physical layout changes
 * or the enforcement of spatial constraints.
 */
internal interface ViewerViewportHostActions {
    fun onViewportSizeChanged(width: Float, height: Float)
    fun clampPan()
}

/**
 * Contract for managing [ViewerConfig] state, providing a bridge to synchronize
 * configuration settings between the public state object and internal logic.
 */
internal interface ViewerConfigContract {
    val viewerConfig: ViewerConfig
    fun updateConfig(newConfig: ViewerConfig)
}

/**
 * Contract for handling user gestures and animated transformations.
 *
 * This interface defines the actions required to update the viewport's pan and zoom state
 * during active user interaction—such as pinching or dragging—as well as during
 * programmatic animation sequences.
 */
internal interface ViewerInteractionContract {
    fun onGestureStart()
    fun onGestureEnd()
    fun onGestureUpdate(zoomChange: Float, panDelta: Offset, pivot: Offset)
    fun onAnimatedZoomFrame(targetZoom: Float, pivot: Offset)
}

/**
 * Interface used by [com.composepdf.internal.ui.PdfLayout] to coordinate viewport geometry,
 * manage layout updates, and initiate page rendering cycles.
 */
internal interface ViewerLayoutController :
    ViewerViewportReadContract,
    ViewerViewportHostActions,
    ViewerRenderRequester

/**
 * Internal bridge interface providing the hoisted [com.composepdf.PdfViewerState] with access
 */
internal interface PdfViewerStateControllerBridge :
    ViewerViewportReadContract,
    ViewerViewportHostActions,
    ViewerRenderRequester,
    ViewerInteractionContract,
    ViewerConfigContract

/**
 * Combined contract for gesture handling, providing read-only access to viewport geometry
 * and methods to process interaction-driven updates.
 */
internal interface ViewerGestureController : ViewerViewportReadContract, ViewerInteractionContract
