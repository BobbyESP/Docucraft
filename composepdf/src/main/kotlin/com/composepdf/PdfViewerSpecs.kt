/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp

/** Direction in which pages are laid out and scrolled. */
enum class ScrollDirection {
    /** Pages are stacked vertically; scroll up/down to navigate. */
    VERTICAL,

    /** Pages are laid out side by side; scroll left/right to navigate. */
    HORIZONTAL,
}

/** How pages are fitted within the viewport at zoom `1f`. */
enum class FitMode {
    /** Scale each page to fill the viewport width. The standard "reader" mode. */
    WIDTH,

    /** Scale each page to fit the viewport height. Width may extend beyond the viewport. */
    HEIGHT,

    /** Scale each page to fit entirely within the viewport. Letterboxing may occur. */
    BOTH,

    /**
     * Preserve each page's size relative to the largest page in the document, so mixed-size
     * documents (A4 + A5 + landscape) keep their true proportions.
     */
    PROPORTIONAL,
}

/**
 * How pages are arranged: direction, fit, spacing and snapping.
 *
 * @property scrollDirection Direction in which pages are laid out and scrolled.
 * @property fitMode How pages are fitted within the viewport at zoom `1f`.
 * @property pageSpacing Gap between consecutive pages.
 * @property pageSnapping When enabled, releasing a scroll near fit zoom settles on a page boundary
 *   (like a pager). Only applies while a whole page fits the viewport along the scroll axis.
 */
@Immutable
data class PdfLayoutSpec(
    val scrollDirection: ScrollDirection = ScrollDirection.VERTICAL,
    val fitMode: FitMode = FitMode.WIDTH,
    val pageSpacing: Dp = PdfViewerDefaults.PageSpacing,
    val pageSnapping: Boolean = false,
)

/**
 * Zoom limits and double-tap behaviour.
 *
 * @property minZoom Lowest committed zoom. Pinching below it is allowed transiently (rubber band)
 *   when [overZoom] is enabled, then springs back on release.
 * @property maxZoom Highest committed zoom.
 * @property doubleTapZoom Zoom level a double tap animates to when the viewer is near fit zoom;
 *   double-tapping again returns to fit.
 * @property overZoom Allow pinching past [minZoom]/[maxZoom] with resistance, springing back to the
 *   limit when the fingers lift — the behaviour of Google Drive and the system photo viewer.
 */
@Immutable
data class PdfZoomSpec(
    val minZoom: Float = PdfViewerDefaults.MinZoom,
    val maxZoom: Float = PdfViewerDefaults.MaxZoom,
    val doubleTapZoom: Float = PdfViewerDefaults.DoubleTapZoom,
    val overZoom: Boolean = true,
)

/**
 * Which gestures the viewer responds to. All are enabled by default; panning is always available.
 *
 * @property isZoomEnabled Master switch for pinch-to-zoom (and centroid panning while pinching).
 * @property isDoubleTapZoomEnabled Double tap toggles between fit and [PdfZoomSpec.doubleTapZoom].
 * @property isQuickScaleEnabled Double tap and hold, then drag vertically to zoom continuously
 *   (one-handed zoom, as in Google Maps/Drive). Dragging down zooms in.
 * @property isFlingEnabled Inertial scrolling after a released pan.
 */
@Immutable
data class PdfGestureSpec(
    val isZoomEnabled: Boolean = true,
    val isDoubleTapZoomEnabled: Boolean = true,
    val isQuickScaleEnabled: Boolean = true,
    val isFlingEnabled: Boolean = true,
)

/**
 * Rendering pipeline tuning.
 *
 * @property quality Oversampling factor for base page bitmaps. At zoom `1f` a page is rasterized at
 *   `pageWidth × quality` pixels (capped internally), so `1.5` keeps pages sharp on dense screens
 *   without tiling.
 * @property prefetchDistance Pages rendered speculatively beyond the visible range in each
 *   direction. Higher values reduce blank pages during fast scrolling at the cost of memory.
 */
@Immutable
data class PdfRenderSpec(
    val quality: Float = PdfViewerDefaults.RenderQuality,
    val prefetchDistance: Int = PdfViewerDefaults.PrefetchDistance,
)
