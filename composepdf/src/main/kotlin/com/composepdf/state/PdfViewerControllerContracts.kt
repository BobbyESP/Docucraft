package com.composepdf.state

import android.util.Size
import androidx.compose.ui.geometry.Offset

/** Read-only viewport/layout contract used by UI and state helpers. */
internal interface ViewerViewportReadContract {
    val viewportWidth: Float
    val viewportHeight: Float
    val pageSizes: List<Size>

    fun pageHeightPx(index: Int): Float
    fun pageWidthPx(index: Int): Float
    fun pageTopDocY(index: Int): Float
    fun visiblePageIndices(): IntRange
    fun isPointOverPage(point: Offset): Boolean
    fun computeCenteredPanForPage(pageIndex: Int): Pair<Float, Float>
    fun computeFitDocumentZoom(): Float
    fun computeFitPageZoom(pageIndex: Int): Float
}

/** Render request entry-point shared by UI callbacks and imperative state APIs. */
internal interface ViewerRenderRequester {
    fun requestRenderForVisiblePages()
}

/** Mutations that affect viewport geometry from layout or programmatic navigation. */
internal interface ViewerViewportHostActions {
    fun onViewportSizeChanged(width: Float, height: Float)
    fun clampPan()
}

/** Configuration commands needed by the hoisted public state object. */
internal interface ViewerConfigContract {
    val viewerConfig: ViewerConfig
    fun updateConfig(newConfig: ViewerConfig)
}

/** Gesture and animation actions consumed by the gesture modifier. */
internal interface ViewerInteractionContract {
    fun onGestureStart()
    fun onGestureEnd()
    fun onGestureUpdate(zoomChange: Float, panDelta: Offset, pivot: Offset)
    fun onAnimatedZoomFrame(targetZoom: Float, pivot: Offset)
}

/** Layout-facing controller used by [com.composepdf.ui.PdfLayout]. */
@Suppress("unused")
internal interface ViewerLayoutController :
    ViewerViewportReadContract,
    ViewerViewportHostActions,
    ViewerRenderRequester

/** Narrow bridge used by [PdfViewerState] for imperative programmatic APIs. */
internal interface PdfViewerStateControllerBridge :
    ViewerViewportReadContract,
    ViewerViewportHostActions,
    ViewerRenderRequester,
    ViewerInteractionContract,
    ViewerConfigContract

/** Combined contract used by gesture handling, which needs reads plus interaction actions. */
internal interface ViewerGestureController : ViewerViewportReadContract, ViewerInteractionContract
