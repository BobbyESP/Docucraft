package com.composepdf.ui

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
import com.composepdf.renderer.tiles.TileKey
import com.composepdf.state.PdfViewerState
import kotlin.math.roundToInt

/**
 * Composable responsible for rendering a single PDF page.
 *
 * Layers two levels of detail on a [Canvas]:
 * 1. **Base bitmap** — a low-resolution full-page render, always visible as a fallback.
 * 2. **Tiles** — high-resolution 256×256 px sub-regions, composited on top at the correct
 *    scale derived from the ratio `currentZoom / tileZoom`.
 *
 * Clipping ([clipToBounds]) prevents tiles from bleeding into the gap between pages.
 *
 * Night-mode color inversion is handled here via [colorFilter], keeping the renderer
 * (CPU-intensive) free from any color-space concerns.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PdfPage(
    state: PdfViewerState,
    bitmap: ImageBitmap?,
    pageIndex: Int,
    pageWidthPx: Float,
    showLoadingIndicator: Boolean,
    modifier: Modifier = Modifier,
    colorFilter: ColorFilter? = null
) {
    Box(
        modifier = modifier
            .clipToBounds()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            val tiles = state.run {
                tileRevision
                getImageBitmapTilesForPage(pageIndex)
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val zoom = state.zoom
                val activeSteppedZoom = state.activeSteppedZoom
                val expectedBaseWidthKey = TileKey.normalizedBaseWidthKey(pageWidthPx)

                // 1. Draw low-resolution base page stretched to fill the measured size.
                drawImage(
                    image = bitmap,
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    colorFilter = colorFilter
                )

                // 2. Composite only this page's tiles that match the active stepped zoom.
                // Strict filtering prevents "Tile Soup" (mixing tiles from different zoom levels).
                tiles.forEach { publishedTile ->
                    val tileKey = publishedTile.tileKey
                    
                    // Filter by zoom level
                    if (tileKey.zoom != activeSteppedZoom) {
                        return@forEach
                    }
                    
                    // Filter by base width to avoid tiles from previous layouts
                    if (tileKey.baseWidthKey != expectedBaseWidthKey && tileKey.baseWidthKey >= 0) {
                        return@forEach
                    }

                    val scale = zoom / tileKey.zoom

                    drawImage(
                        image = publishedTile.imageBitmap,
                        dstOffset = IntOffset(
                            (tileKey.rect.left * scale).roundToInt(),
                            (tileKey.rect.top * scale).roundToInt()
                        ),
                        dstSize = IntSize(
                            (tileKey.rect.width() * scale).roundToInt(),
                            (tileKey.rect.height() * scale).roundToInt()
                        ),
                        colorFilter = colorFilter
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
