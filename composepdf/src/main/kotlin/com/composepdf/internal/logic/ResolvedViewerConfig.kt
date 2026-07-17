/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.internal.logic

import com.composepdf.FitMode
import com.composepdf.PdfLayoutSpec
import com.composepdf.PdfRenderSpec
import com.composepdf.PdfZoomSpec
import com.composepdf.ScrollDirection

/**
 * The public specs flattened into the primitive values the geometry and rendering layers consume.
 * Density is resolved at composition time so nothing below the UI ever touches Compose units.
 */
internal data class ResolvedViewerConfig(
    val scrollDirection: ScrollDirection = ScrollDirection.VERTICAL,
    val fitMode: FitMode = FitMode.WIDTH,
    val pageSpacingPx: Float = 0f,
    val pageSnapping: Boolean = false,
    val minZoom: Float = 1f,
    val maxZoom: Float = 8f,
    val doubleTapZoom: Float = 2.5f,
    val renderQuality: Float = 1.5f,
    val prefetchDistance: Int = 2,
) {
    companion object {
        fun from(
            layout: PdfLayoutSpec,
            zoom: PdfZoomSpec,
            render: PdfRenderSpec,
            pageSpacingPx: Float,
        ): ResolvedViewerConfig =
            ResolvedViewerConfig(
                scrollDirection = layout.scrollDirection,
                fitMode = layout.fitMode,
                pageSpacingPx = pageSpacingPx,
                pageSnapping = layout.pageSnapping,
                minZoom = zoom.minZoom,
                maxZoom = zoom.maxZoom,
                doubleTapZoom = zoom.doubleTapZoom,
                renderQuality = render.quality,
                prefetchDistance = render.prefetchDistance,
            )
    }
}
