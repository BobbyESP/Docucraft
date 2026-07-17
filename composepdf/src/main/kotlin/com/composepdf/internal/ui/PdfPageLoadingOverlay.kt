/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.internal.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import com.composepdf.PdfViewerDefaults
import com.composepdf.PdfViewerState
import com.composepdf.internal.engine.TileDraw
import com.composepdf.internal.logic.ViewerController
import kotlin.math.roundToInt

/**
 * Centers a small indicator on every visible page that has no content yet (no base bitmap and no
 * tiles).
 *
 * The set of pending pages is derived state — it only recomposes when a page starts/stops being
 * pending. Following the pan/zoom happens in the placement block, so scrolling never recomposes
 * this overlay.
 */
@Composable
internal fun PdfPageLoadingOverlay(
    controller: ViewerController,
    state: PdfViewerState,
    pages: State<Map<Int, ImageBitmap>>,
    tiles: State<Map<Int, List<TileDraw>>>,
    modifier: Modifier = Modifier,
) {
    val pendingPages by
        remember(controller) {
            derivedStateOf {
                val rendered = pages.value
                val tileMap = tiles.value
                controller.visiblePageIndices().filter { page ->
                    rendered[page] == null && tileMap[page].isNullOrEmpty()
                }
            }
        }

    if (pendingPages.isEmpty()) return

    Layout(
        modifier = modifier,
        content = {
            for (page in pendingPages) {
                key(page) {
                    Box(Modifier.layoutId(page)) { PdfViewerDefaults.PageLoadingIndicator() }
                }
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { it to it.measure(Constraints()) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            val layoutSnapshot = controller.layout()
            if (layoutSnapshot.isEmpty) return@layout
            val zoom = state.zoom
            val panX = state.panX
            val panY = state.panY

            for ((measurable, placeable) in placeables) {
                val page = measurable.layoutId as? Int ?: continue
                val centerX =
                    layoutSnapshot.pageScreenLeft(page, panX, zoom) +
                        layoutSnapshot.pageWidthPx(page) * zoom / 2f
                val centerY =
                    layoutSnapshot.pageScreenTop(page, panY, zoom) +
                        layoutSnapshot.pageHeightPx(page) * zoom / 2f
                placeable.place(
                    (centerX - placeable.width / 2f).roundToInt(),
                    (centerY - placeable.height / 2f).roundToInt(),
                )
            }
        }
    }
}
