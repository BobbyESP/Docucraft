/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.internal.logic

import androidx.compose.ui.geometry.Offset
import com.composepdf.ViewerConfig

/**
 * The single contract between [PdfViewerController] and everything that talks to it: the hoisted
 * [com.composepdf.PdfViewerState], the layout composable and the gesture modifier.
 */
internal interface ViewerController {
    val viewerConfig: ViewerConfig
    val viewportWidth: Float
    val viewportHeight: Float

    fun pageWidthPx(index: Int): Float

    fun pageHeightPx(index: Int): Float

    fun pageTopDocY(index: Int): Float

    fun pageLeftDocX(index: Int): Float

    fun corridorBreadth(): Float

    fun visiblePageIndices(): IntRange

    fun isPointOverPage(point: Offset): Boolean

    fun computeCenteredPanForPage(pageIndex: Int): Pair<Float, Float>

    fun computeFitDocumentZoom(): Float

    fun computeFitPageZoom(pageIndex: Int): Float

    fun onViewportSizeChanged(width: Float, height: Float)

    fun requestRenderForVisiblePages()

    fun clampPan()

    fun onGestureStart()

    fun onGestureEnd()

    fun onGestureUpdate(zoomChange: Float, panDelta: Offset, pivot: Offset)

    fun onAnimatedZoomFrame(targetZoom: Float, pivot: Offset)

    fun updateConfig(newConfig: ViewerConfig)
}
