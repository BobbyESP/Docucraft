package com.composepdf.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.composepdf.state.PdfViewerState
import kotlin.math.roundToInt

/**
 * Composable responsible for rendering a single PDF page.
 *
 * It layers a low-resolution base bitmap with multiple high-resolution tiles.
 * Clipping is applied to prevent tiles from bleeding into page margins.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PdfPage(
    state: PdfViewerState,
    bitmap: Bitmap?,
    pageIndex: Int,
    showLoadingIndicator: Boolean,
    modifier: Modifier = Modifier,
    colorFilter: ColorFilter? = null
) {
    Box(
        modifier = modifier
            .clipToBounds() // prevents tiles from bleeding into gaps between pages
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Read state within DrawScope to avoid unnecessary recompositions
                val zoom = state.zoom
                val tiles = state.getAllTiles()
                val revision = state.tileRevision // Triggers redraw when new tiles arrive

                // 1. Draw base low-res layer
                drawImage(
                    image = bitmap.asImageBitmap(),
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    colorFilter = colorFilter
                )

                // 2. Draw high-res tiles with dynamic scaling
                tiles.forEach { (key, tileBitmap) ->
                    if (key.startsWith("${pageIndex}_")) {
                        val parts = key.split("_")
                        if (parts.size >= 6) {
                            val tileL = parts[1].toInt()
                            val tileT = parts[2].toInt()
                            val tileR = parts[3].toInt()
                            val tileB = parts[4].toInt()
                            val tileZoom = parts[5].toFloat()

                            val scale = zoom / tileZoom

                            drawImage(
                                image = tileBitmap.asImageBitmap(),
                                dstOffset = IntOffset(
                                    (tileL * scale).roundToInt(),
                                    (tileT * scale).roundToInt()
                                ),
                                dstSize = IntSize(
                                    ((tileR - tileL) * scale).roundToInt(),
                                    ((tileB - tileT) * scale).roundToInt()
                                ),
                                colorFilter = colorFilter
                            )
                        }
                    }
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

@Composable
internal fun PagePlaceholder(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(Color(0xFFF5F5F5)),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 4.dp
        )
    }
}
