/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * What an overlay above the pages can do besides being a [BoxScope]: draw and place things in *page
 * coordinates*, following the document as it pans and zooms.
 *
 * Page coordinates are normalized to the page as it is displayed: `[0, 1] × [0, 1]` from its top
 * left corner, as in [PdfTapEvent.pagePosition]. They do not change with zoom, so what is anchored
 * to them only has to change when it moves on the page.
 *
 * Following the document happens in the draw phase ([DrawOnPages]) and the placement phase
 * ([anchorTo]): panning, zooming and flinging never recompose the overlay.
 */
@Stable
interface PdfOverlayScope : BoxScope {

    /**
     * A layer the size of the viewer that runs [onDraw] for every visible page, clipped to it. For
     * highlights and other marks on the page.
     */
    @Composable fun DrawOnPages(onDraw: PdfPageDrawScope.() -> Unit)

    /**
     * Places this element at [position] on page [pageIndex], with its [alignment] point there:
     * [Alignment.Center] centres it on the point, [Alignment.TopCenter] hangs it below. For
     * selection handles, or a popup beside a link. It is not placed, so not shown, while the page
     * has no layout.
     *
     * @param stayInside Keeps the element within the viewer, sliding it along an edge rather than
     *   letting it be cut off: for a popup, which must be read whole, and not for a handle, which
     *   must stay on its point.
     */
    fun Modifier.anchorTo(
        pageIndex: Int,
        position: Offset,
        alignment: Alignment = Alignment.Center,
        stayInside: Boolean = false,
    ): Modifier

    /**
     * Sizes and places this element to cover [area] of page [pageIndex], normalized to the page,
     * following pan and zoom. For what has to match a region of the page rather than sit at a
     * point: the nodes that let TalkBack reach the links on a page, for instance. Nothing is drawn
     * or touched by it; the element decides that.
     */
    fun Modifier.coverArea(pageIndex: Int, area: Rect): Modifier
}

/**
 * Drawing over one page. Coordinates are the overlay's pixels; map page positions with [toViewer].
 */
interface PdfPageDrawScope : DrawScope {
    /** The page being drawn over. */
    val pageIndex: Int

    /** The page's bounds on screen. */
    val pageBounds: Rect

    /** Where [pagePosition], normalized to the page, is on screen. */
    fun toViewer(pagePosition: Offset): Offset =
        Offset(
            pageBounds.left + pagePosition.x * pageBounds.width,
            pageBounds.top + pagePosition.y * pageBounds.height,
        )

    /** Where [pageRect], normalized to the page, is on screen. */
    fun toViewer(pageRect: Rect): Rect =
        Rect(toViewer(pageRect.topLeft), toViewer(pageRect.bottomRight))
}
