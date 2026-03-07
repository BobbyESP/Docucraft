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
 * Composable responsible for rendering a single PDF page with industrial-grade precision.
 *
 * This implementation avoids "seams" (1px gaps) between tiles by calculating 
 * destinations in a way that ensures adjacent tiles share pixel boundaries perfectly.
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
            // Observe tileRevision to trigger recomposition when the tile cache changes
            val tiles = state.run {
                tileRevision
                getImageBitmapTilesForPage(pageIndex)
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val zoom = state.zoom
                val activeSteppedZoom = state.activeSteppedZoom
                val expectedBaseWidthKey = TileKey.normalizedBaseWidthKey(pageWidthPx)

                // 1. Draw base low-resolution page. 
                // Using the full canvas size ensures the base page covers the background entirely.
                drawImage(
                    image = bitmap,
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    colorFilter = colorFilter
                )

                // 2. Draw high-resolution tiles.
                // We calculate boundaries precisely to avoid sub-pixel gaps.
                tiles.forEach { publishedTile ->
                    val tileKey = publishedTile.tileKey
                    
                    // Filter: Only draw tiles from the current active zoom level and current layout width
                    if (tileKey.zoom != activeSteppedZoom) return@forEach
                    if (tileKey.baseWidthKey != expectedBaseWidthKey && tileKey.baseWidthKey >= 0) return@forEach

                    val scale = zoom / tileKey.zoom

                    // destination boundaries calculated with high precision
                    val left = (tileKey.rect.left * scale).roundToInt()
                    val top = (tileKey.rect.top * scale).roundToInt()
                    val right = (tileKey.rect.right * scale).roundToInt()
                    val bottom = (tileKey.rect.bottom * scale).roundToInt()

                    drawImage(
                        image = publishedTile.imageBitmap,
                        dstOffset = IntOffset(left, top),
                        dstSize = IntSize(right - left, bottom - top),
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
