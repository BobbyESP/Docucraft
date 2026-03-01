package com.composepdf

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Contains default values for [PdfViewer] configuration.
 * 
 * Per Compose Component API Guidelines, all component default expressions
 * should live in a top-level object named `ComponentDefaults`.
 * 
 * Example usage:
 * ```kotlin
 * PdfViewer(
 *     source = source,
 *     config = ViewerConfig(
 *         maxZoom = PdfViewerDefaults.MaxZoom * 2,
 *         pageSpacing = PdfViewerDefaults.PageSpacing
 *     )
 * )
 * ```
 */
object PdfViewerDefaults {
    
    /**
     * Default spacing between pages.
     */
    val PageSpacing: Dp = 8.dp
    
    /**
     * Default DPI multiplier for render quality.
     */
    const val RenderQuality: Float = 1.5f
    
    /**
     * Default minimum zoom level.
     */
    const val MinZoom: Float = 1f
    
    /**
     * Default maximum zoom level.
     */
    const val MaxZoom: Float = 5f
    
    /**
     * Default zoom level applied on double-tap.
     */
    const val DoubleTapZoom: Float = 2.5f
    
    /**
     * Default number of pages to prefetch on each side.
     */
    const val PrefetchDistance: Int = 2
}
