/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.internal.logic

import android.util.Size
import com.composepdf.ScrollDirection

/**
 * Pages of the given sizes laid out one after another, [spacing] px apart along [scrollDirection],
 * with offsets, document size and corridor derived as the real layout derives them.
 */
internal fun layoutOf(
    widths: FloatArray,
    heights: FloatArray,
    spacing: Float,
    viewport: ViewportMetrics,
    scrollDirection: ScrollDirection = ScrollDirection.VERTICAL,
): PageLayoutSnapshot {
    val vertical = scrollDirection == ScrollDirection.VERTICAL
    val along = if (vertical) heights else widths
    val across = if (vertical) widths else heights
    val offsets = FloatArray(along.size)
    var next = 0f
    for (index in along.indices) {
        offsets[index] = next
        next += along[index] + spacing
    }
    return PageLayoutSnapshot(
        pageSizes = List(along.size) { Size(1, 1) },
        pageOffsets = offsets,
        pageHeights = heights,
        pageWidths = widths,
        totalDocumentSize = (next - spacing).coerceAtLeast(0f),
        corridorBreadth = across.max(),
        viewport = viewport,
        pageSpacingPx = spacing,
        scrollDirection = scrollDirection,
    )
}

/** Three 500 × 500 pages, 20 px apart, stacked vertically: the document is 1540 px long. */
internal fun threePages(viewport: ViewportMetrics = ViewportMetrics(500f, 500f)) =
    layoutOf(
        widths = FloatArray(3) { 500f },
        heights = FloatArray(3) { 500f },
        spacing = 20f,
        viewport = viewport,
    )
