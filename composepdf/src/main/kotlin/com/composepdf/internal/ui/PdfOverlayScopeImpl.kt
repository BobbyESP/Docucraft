/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.internal.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.composepdf.PdfOverlayScope
import com.composepdf.PdfPageDrawScope
import com.composepdf.PdfViewerState
import kotlin.math.roundToInt

/**
 * The overlay's scope. Pages are located through [PdfViewerState.pageRectInViewer], which reads pan
 * and zoom: read inside a draw or placement block, that is what makes those blocks, and only them,
 * run again when the document moves.
 */
internal class PdfOverlayScopeImpl(box: BoxScope, private val state: PdfViewerState) :
    PdfOverlayScope, BoxScope by box {

    @Composable
    override fun DrawOnPages(onDraw: PdfPageDrawScope.() -> Unit) {
        Spacer(
            Modifier.matchParentSize().drawBehind {
                for (page in state.visiblePages) {
                    val bounds = state.pageRectInViewer(page) ?: continue
                    clipRect(bounds.left, bounds.top, bounds.right, bounds.bottom) {
                        PageDrawScope(this, page, bounds).onDraw()
                    }
                }
            }
        )
    }

    override fun Modifier.anchorTo(
        pageIndex: Int,
        position: Offset,
        alignment: Alignment,
        stayInside: Boolean,
    ): Modifier =
        align(Alignment.TopStart).layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            // The viewer's size, from the engine: not from these constraints, which are the
            // element's own if a size modifier comes before this one in the chain.
            val room =
                state.controller?.let {
                    IntSize(it.viewportWidth.roundToInt(), it.viewportHeight.roundToInt())
                } ?: IntSize(constraints.maxWidth, constraints.maxHeight)
            layout(placeable.width, placeable.height) {
                val page = state.pageRectInViewer(pageIndex) ?: return@layout
                val point =
                    IntOffset(
                        (page.left + position.x * page.width).roundToInt(),
                        (page.top + position.y * page.height).roundToInt(),
                    )
                // Aligning the element in no space at all gives minus the offset of its alignment
                // point: exactly what puts that point on the anchor.
                val lead =
                    alignment.align(
                        IntSize(placeable.width, placeable.height),
                        IntSize.Zero,
                        layoutDirection,
                    )
                val at = point + lead
                placeable.place(
                    if (stayInside) {
                        IntOffset(
                            at.x.coerceIn(0, (room.width - placeable.width).coerceAtLeast(0)),
                            at.y.coerceIn(0, (room.height - placeable.height).coerceAtLeast(0)),
                        )
                    } else {
                        at
                    }
                )
            }
        }

    /**
     * Measured, not only placed, from the page: its size changes with the zoom. Reading the page's
     * bounds while measuring makes it measure again as the document moves, and never recompose.
     */
    override fun Modifier.coverArea(pageIndex: Int, area: Rect): Modifier =
        align(Alignment.TopStart).layout { measurable, _ ->
            val page = state.pageRectInViewer(pageIndex)
            val width = page?.let { (area.width * it.width).roundToInt().coerceAtLeast(0) } ?: 0
            val height = page?.let { (area.height * it.height).roundToInt().coerceAtLeast(0) } ?: 0
            val placeable = measurable.measure(Constraints.fixed(width, height))
            layout(width, height) {
                if (page == null) return@layout
                placeable.place(
                    IntOffset(
                        (page.left + area.left * page.width).roundToInt(),
                        (page.top + area.top * page.height).roundToInt(),
                    )
                )
            }
        }
}

private class PageDrawScope(
    scope: DrawScope,
    override val pageIndex: Int,
    override val pageBounds: Rect,
) : PdfPageDrawScope, DrawScope by scope
