package com.composepdf

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Default values for [PdfViewer] configuration and appearance.
 *
 * Per Compose Component API Guidelines, all default expressions live in a
 * top-level object named `ComponentDefaults`.
 *
 * Example:
 * ```kotlin
 * PdfViewer(
 *     source = source,
 *     config = ViewerConfig(
 *         maxZoom    = PdfViewerDefaults.MaxZoom * 2,
 *         pageSpacing = PdfViewerDefaults.PageSpacing
 *     )
 * )
 * ```
 */
object PdfViewerDefaults {

    /**
     * Background color of the viewer container (shown around pages and while loading).
     * A dark neutral gray that provides good contrast for both light and dark pages.
     */
    val ViewerBackground: Color = Color(0xFF424242)

    /** Default spacing between consecutive pages. */
    val PageSpacing: Dp = 8.dp

    /**
     * Base oversampling factor for base-page rendering.
     *
     * At zoom = 1 the bitmap will be `viewportWidth × RenderQuality` pixels wide.
     * At high zoom levels the bitmap is capped at [com.composepdf.renderer.PageRenderer.MAX_BITMAP_PX]
     * so this factor is effectively reduced automatically — no OOM risk.
     *
     * 1.5 = 50 % oversampling → sharp on FullHD / QHD screens at zoom = 1.
     */
    const val RenderQuality: Float = 1.5f

    /** Minimum zoom level. Pages cannot be zoomed out further than this. */
    const val MinZoom: Float = 1f

    /** Maximum zoom level. Pages cannot be zoomed in further than this. */
    const val MaxZoom: Float = 5f

    /**
     * Zoom level applied on the first double-tap.
     *
     * The double-tap gesture cycles through three levels:
     *   1. fit-page zoom → [DoubleTapZoom]
     *   2. [DoubleTapZoom] → [MaxZoom]
     *   3. [MaxZoom] → fit-page zoom
     */
    const val DoubleTapZoom: Float = 2.5f

    /**
     * Number of pages to render speculatively beyond the visible range in each direction.
     * Higher values reduce blank-page flicker during fast scrolling at the cost of memory.
     */
    const val PrefetchDistance: Int = 2
}
