package com.composepdf.state

import androidx.compose.ui.unit.Dp
import com.composepdf.PdfViewerDefaults

/**
 * Configuration options for the PDF viewer.
 * 
 * This data class allows customization of scrolling behavior, zoom limits,
 * rendering quality, and visual appearance of the PDF viewer.
 * 
 * Default values are provided by [PdfViewerDefaults].
 * 
 * Example usage:
 * ```kotlin
 * val config = ViewerConfig(
 *     scrollDirection = ScrollDirection.VERTICAL,
 *     fitMode = FitMode.WIDTH,
 *     isNightModeEnabled = true,
 *     maxZoom = 6f
 * )
 * ```
 */
data class ViewerConfig(
    /**
     * The direction of scrolling between pages.
     */
    val scrollDirection: ScrollDirection = ScrollDirection.VERTICAL,
    
    /**
     * How pages should be fitted within the viewport.
     */
    val fitMode: FitMode = FitMode.WIDTH,
    
    /**
     * Spacing between pages.
     */
    val pageSpacing: Dp = PdfViewerDefaults.PageSpacing,
    
    /**
     * Whether page snapping is enabled (snap to full pages when scrolling).
     */
    val isPageSnappingEnabled: Boolean = false,
    
    /**
     * Whether night mode is enabled (inverts colors for dark viewing).
     */
    val isNightModeEnabled: Boolean = false,
    
    /**
     * DPI multiplier for render quality.
     * Higher values produce sharper renders but use more memory.
     * Recommended range: 1.0 to 2.0
     */
    val renderQuality: Float = PdfViewerDefaults.RenderQuality,
    
    /**
     * Minimum allowed zoom level (1.0 = 100%).
     */
    val minZoom: Float = PdfViewerDefaults.MinZoom,
    
    /**
     * Maximum allowed zoom level.
     */
    val maxZoom: Float = PdfViewerDefaults.MaxZoom,
    
    /**
     * Zoom level to apply on double-tap.
     */
    val doubleTapZoom: Float = PdfViewerDefaults.DoubleTapZoom,
    
    /**
     * Number of pages to prefetch on each side of visible pages.
     */
    val prefetchDistance: Int = PdfViewerDefaults.PrefetchDistance,
    
    /**
     * Whether zoom gestures are enabled.
     */
    val isZoomGesturesEnabled: Boolean = true,
    
    /**
     * Whether loading indicators are visible while pages are rendering.
     */
    val isLoadingIndicatorVisible: Boolean = true
)

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
     * Page height may extend beyond the viewport.
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
    BOTH
}
