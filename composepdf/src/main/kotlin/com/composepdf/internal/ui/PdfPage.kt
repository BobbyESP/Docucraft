/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.internal.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.composepdf.PdfViewerState
import com.composepdf.internal.engine.TileDraw
import kotlin.math.roundToInt

/**
 * Draws one PDF page: the base bitmap stretched to the page bounds, then the engine-published tiles
 * on top.
 *
 * The published tile list may contain tiles from a previous zoom level while the current level is
 * still rendering; because destination rectangles are derived from the live [PdfViewerState.zoom]
 * against each tile's own scale, stale tiles simply appear scaled — which is what makes pinching
 * feel continuous instead of flashing to a low-resolution page.
 *
 * Tile edges are computed from grid coordinates with a single rounding rule, so adjacent tiles
 * always share the exact same destination pixel and no seams appear.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PdfPage(
    state: PdfViewerState,
    bitmap: ImageBitmap?,
    tiles: List<TileDraw>,
    showLoadingIndicator: Boolean,
    modifier: Modifier = Modifier,
    colorFilter: ColorFilter? = null,
) {
    Box(
        modifier = modifier.clipToBounds().background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null || tiles.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val zoom = state.zoom

                if (bitmap != null) {
                    drawImage(
                        image = bitmap,
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                        colorFilter = colorFilter,
                    )
                }

                for (tile in tiles) {
                    val scale = zoom / tile.levelScale
                    val left = (tile.left * scale).roundToInt()
                    val top = (tile.top * scale).roundToInt()
                    val right = (tile.right * scale).roundToInt()
                    val bottom = (tile.bottom * scale).roundToInt()
                    if (right <= left || bottom <= top) continue

                    drawImage(
                        image = tile.image,
                        dstOffset = IntOffset(left, top),
                        dstSize = IntSize(right - left, bottom - top),
                        colorFilter = colorFilter,
                    )
                }
            }
        } else if (showLoadingIndicator) {
            CircularWavyProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
