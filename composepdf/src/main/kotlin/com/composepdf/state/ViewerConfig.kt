package com.composepdf.state

import androidx.compose.ui.unit.Dp
import com.composepdf.PdfViewerDefaults

data class ViewerConfig(
    val scrollDirection: ScrollDirection = ScrollDirection.VERTICAL,
    val fitMode: FitMode = FitMode.WIDTH,
    val pageSpacing: Dp = PdfViewerDefaults.PageSpacing,
    val isPageSnappingEnabled: Boolean = false,
    val isNightModeEnabled: Boolean = false,
    val renderQuality: Float = PdfViewerDefaults.RenderQuality,
    val minZoom: Float = PdfViewerDefaults.MinZoom,
    val maxZoom: Float = PdfViewerDefaults.MaxZoom,
    val doubleTapZoom: Float = PdfViewerDefaults.DoubleTapZoom,
    val prefetchDistance: Int = PdfViewerDefaults.PrefetchDistance,
    val isZoomGesturesEnabled: Boolean = true,
    val isLoadingIndicatorVisible: Boolean = true,
    /** Screen density used to convert [pageSpacing] Dp → pixels. Set automatically by PdfViewer. */
    internal val density: Float = 1f
) {
    /**
     * [pageSpacing] converted to screen pixels using the current screen density.
     * Used by [com.composepdf.state.PdfViewerController] for layout geometry calculations.
     */
    val pageSpacingPx: Float get() = pageSpacing.value * density
}

/**
 * Direction of scrolling/swiping between pages.
 */
enum class ScrollDirection {
    /**
     * Pages are arranged vertically, scroll up/down to navigate.
     */
    VERTICAL,
    
    /**
     * Pages are arranged horizontally, scroll left/right to navigate.
     */
    HORIZONTAL
}

/**
 * How pages should be fitted within the viewport.
 */
enum class FitMode {
    /**
     * Scale page to fit the viewport width.
     * All pages fill the full width regardless of their actual dimensions.
     * This is the standard "reader" mode.
     */
    WIDTH,
    
    /**
     * Scale page to fit the viewport height.
     * Page width may extend beyond the viewport.
     */
    HEIGHT,
    
    /**
     * Scale page to fit entirely within the viewport.
     * Letterboxing may occur.
     */
    BOTH,

    /**
     * Preserve each page's width relative to the widest page in the document.
     * A page that is half as wide as the widest page will occupy half the viewport width.
     * This makes mixed-size documents (e.g. A4 + A5 + landscape) look correct.
     */
    PROPORTIONAL
}
