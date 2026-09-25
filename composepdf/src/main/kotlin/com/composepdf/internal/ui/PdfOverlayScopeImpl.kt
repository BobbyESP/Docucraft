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
    ): Modifier =
        align(Alignment.TopStart).layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
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
                placeable.place(point + lead)
            }
        }
}

private class PageDrawScope(
    scope: DrawScope,
    override val pageIndex: Int,
    override val pageBounds: Rect,
) : PdfPageDrawScope, DrawScope by scope
