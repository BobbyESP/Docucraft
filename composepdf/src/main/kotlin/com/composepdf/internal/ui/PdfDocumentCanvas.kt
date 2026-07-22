/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.internal.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.composepdf.PdfViewerState
import com.composepdf.PdfViewerStyle
import com.composepdf.ScrollDirection
import com.composepdf.internal.engine.TileDraw
import com.composepdf.internal.logic.PageLayoutSnapshot
import com.composepdf.internal.logic.ViewerController
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Draws the whole document — page shadows, paper, base bitmaps, tiles and the scroll indicator — in
 * a single draw node.
 *
 * Pan, zoom and newly published bitmaps are read *inside* the draw block, so interaction and
 * rendering invalidate only drawing: no recomposition, no relayout, no allocation per frame. Tiles
 * from other zoom levels stay visible scaled by `zoom / levelScale` until the current level covers
 * them, which is what makes pinching feel continuous. Tile edges are computed from grid coordinates
 * with a single rounding rule, so adjacent tiles never show seams.
 */
@Composable
internal fun PdfDocumentCanvas(
    controller: ViewerController,
    state: PdfViewerState,
    pages: State<Map<Int, ImageBitmap>>,
    tiles: State<Map<Int, List<TileDraw>>>,
    style: PdfViewerStyle,
    scrollIndicatorAlpha: () -> Float,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    val nightFilter =
        remember(style.nightMode) {
            if (style.nightMode) ColorFilter.colorMatrix(ColorMatrix(NIGHT_MATRIX)) else null
        }
    val paperColor =
        remember(style.pageColor, style.nightMode) {
            if (style.nightMode) style.pageColor.inverted() else style.pageColor
        }

    val cornerPx = with(density) { style.pageCornerRadius.toPx() }
    val shadowOffsetPx = with(density) { SHADOW_OFFSET.toPx() }
    val shadowSpreadPx = with(density) { SHADOW_SPREAD.toPx() }
    val indicator = style.scrollIndicator
    val indicatorThicknessPx = indicator?.let { with(density) { it.thickness.toPx() } } ?: 0f
    val indicatorPaddingPx = indicator?.let { with(density) { it.padding.toPx() } } ?: 0f
    val indicatorMinLengthPx = indicator?.let { with(density) { it.minLength.toPx() } } ?: 0f

    val clipPath = remember { Path() }

    Spacer(
        modifier.fillMaxSize().drawBehind {
            val layout = controller.layout()
            if (layout.isEmpty) return@drawBehind

            val zoom = state.zoom
            val panX = state.panX
            val panY = state.panY
            val visible = layout.visiblePageIndices(panX, panY, zoom)
            val pagesMap = pages.value
            val tilesMap = tiles.value

            for (page in visible) {
                drawPage(
                    layout = layout,
                    page = page,
                    panX = panX,
                    panY = panY,
                    zoom = zoom,
                    base = pagesMap[page],
                    pageTiles = tilesMap[page],
                    paperColor = paperColor,
                    shadowColor = style.pageShadowColor,
                    shadowOffsetPx = shadowOffsetPx,
                    shadowSpreadPx = shadowSpreadPx,
                    cornerPx = cornerPx,
                    clipPath = clipPath,
                    nightFilter = nightFilter,
                )
            }

            if (indicator != null) {
                val alpha = scrollIndicatorAlpha()
                if (alpha > 0.01f) {
                    drawScrollIndicator(
                        layout = layout,
                        panX = panX,
                        panY = panY,
                        zoom = zoom,
                        color = indicator.color,
                        alpha = alpha,
                        thicknessPx = indicatorThicknessPx,
                        paddingPx = indicatorPaddingPx,
                        minLengthPx = indicatorMinLengthPx,
                    )
                }
            }
        }
    )
}

private fun DrawScope.drawPage(
    layout: PageLayoutSnapshot,
    page: Int,
    panX: Float,
    panY: Float,
    zoom: Float,
    base: ImageBitmap?,
    pageTiles: List<TileDraw>?,
    paperColor: Color,
    shadowColor: Color,
    shadowOffsetPx: Float,
    shadowSpreadPx: Float,
    cornerPx: Float,
    clipPath: Path,
    nightFilter: ColorFilter?,
) {
    val left = layout.pageScreenLeft(page, panX, zoom)
    val top = layout.pageScreenTop(page, panY, zoom)
    val width = layout.pageWidthPx(page) * zoom
    val height = layout.pageHeightPx(page) * zoom
    if (width < 1f || height < 1f) return

    // Soft two-layer drop shadow: a wide faint halo plus a tighter offset core.
    if (shadowColor.alpha > 0f) {
        drawRoundRect(
            color = shadowColor.copy(alpha = shadowColor.alpha * 0.35f),
            topLeft = Offset(left - shadowSpreadPx, top - shadowSpreadPx + shadowOffsetPx),
            size = Size(width + shadowSpreadPx * 2f, height + shadowSpreadPx * 2f),
            cornerRadius = CornerRadius(cornerPx + shadowSpreadPx),
        )
        drawRoundRect(
            color = shadowColor.copy(alpha = shadowColor.alpha * 0.65f),
            topLeft = Offset(left, top + shadowOffsetPx),
            size = Size(width, height),
            cornerRadius = CornerRadius(cornerPx),
        )
    }

    val leftI = left.roundToInt()
    val topI = top.roundToInt()
    val widthI = width.roundToInt().coerceAtLeast(1)
    val heightI = height.roundToInt().coerceAtLeast(1)

    // Manual save/clip/restore keeps this loop allocation-free (no clip-block lambdas).
    val canvas = drawContext.canvas
    canvas.save()
    if (cornerPx > 0.5f) {
        clipPath.rewind()
        clipPath.addRoundRect(
            RoundRect(left, top, left + width, top + height, CornerRadius(cornerPx))
        )
        canvas.clipPath(clipPath)
    } else {
        canvas.clipRect(left, top, left + width, top + height)
    }

    drawRect(color = paperColor, topLeft = Offset(left, top), size = Size(width, height))

    if (base != null) {
        drawImage(
            image = base,
            dstOffset = IntOffset(leftI, topI),
            dstSize = IntSize(widthI, heightI),
            colorFilter = nightFilter,
        )
    }

    if (pageTiles != null) {
        for (i in pageTiles.indices) {
            val tile = pageTiles[i]
            val scale = zoom / tile.levelScale
            val tileLeft = leftI + (tile.left * scale).roundToInt()
            val tileTop = topI + (tile.top * scale).roundToInt()
            val tileRight = leftI + (tile.right * scale).roundToInt()
            val tileBottom = topI + (tile.bottom * scale).roundToInt()
            if (tileRight <= tileLeft || tileBottom <= tileTop) continue

            drawImage(
                image = tile.image,
                dstOffset = IntOffset(tileLeft, tileTop),
                dstSize = IntSize(tileRight - tileLeft, tileBottom - tileTop),
                colorFilter = nightFilter,
            )
        }
    }

    canvas.restore()
}

private fun DrawScope.drawScrollIndicator(
    layout: PageLayoutSnapshot,
    panX: Float,
    panY: Float,
    zoom: Float,
    color: Color,
    alpha: Float,
    thicknessPx: Float,
    paddingPx: Float,
    minLengthPx: Float,
) {
    val vertical = layout.scrollDirection == ScrollDirection.VERTICAL
    val contentSpan = layout.totalDocumentSize * zoom
    val viewportSpan = if (vertical) size.height else size.width
    if (contentSpan <= viewportSpan * 1.01f) return

    val track = viewportSpan - paddingPx * 2f
    val thumbLength = max(minLengthPx, track * viewportSpan / contentSpan)
    val maxScroll = contentSpan - viewportSpan
    val fraction = ((if (vertical) -panY else -panX) / maxScroll).coerceIn(0f, 1f)
    val thumbStart = paddingPx + (track - thumbLength) * fraction
    val tintedColor = color.copy(alpha = color.alpha * alpha)
    val radius = CornerRadius(thicknessPx / 2f)

    if (vertical) {
        drawRoundRect(
            color = tintedColor,
            topLeft = Offset(size.width - paddingPx - thicknessPx, thumbStart),
            size = Size(thicknessPx, thumbLength),
            cornerRadius = radius,
        )
    } else {
        drawRoundRect(
            color = tintedColor,
            topLeft = Offset(thumbStart, size.height - paddingPx - thicknessPx),
            size = Size(thumbLength, thicknessPx),
            cornerRadius = radius,
        )
    }
}

private fun Color.inverted(): Color = Color(1f - red, 1f - green, 1f - blue, alpha)

/** Color inversion matrix used for night mode. */
private val NIGHT_MATRIX =
    floatArrayOf(
        -1f,
        0f,
        0f,
        0f,
        255f,
        0f,
        -1f,
        0f,
        0f,
        255f,
        0f,
        0f,
        -1f,
        0f,
        255f,
        0f,
        0f,
        0f,
        1f,
        0f,
    )

/** Vertical offset of the page shadow. */
private val SHADOW_OFFSET = 2.dp

/** How far the outer shadow layer extends past the page bounds. */
private val SHADOW_SPREAD = 3.dp
