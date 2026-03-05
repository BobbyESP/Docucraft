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
import androidx.compose.runtime.remember
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
            val baseImageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

            // Read tileRevision so Compose schedules a redraw
            // when new tiles arrive.
            val tiles = state.run {
                tileRevision
                getAllImageBitmapTiles()
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val zoom = state.zoom

                drawImage(
                    image = baseImageBitmap,
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    colorFilter = colorFilter
                )

                tiles.forEach { (key, tileImageBitmap) ->
                    if (key.startsWith("${pageIndex}_")) {
                        val parts = key.split("_")
                        if (parts.size >= 6) {
                            val tileL = parts[1].toIntOrNull()
                            val tileT = parts[2].toIntOrNull()
                            val tileR = parts[3].toIntOrNull()
                            val tileB = parts[4].toIntOrNull()
                            val tileZoom = parts[5].toFloatOrNull()

                            if (tileL == null || tileT == null || tileR == null || tileB == null || tileZoom == null) {
                                return@forEach
                            }

                            val scale = zoom / tileZoom

                            drawImage(
                                image = tileImageBitmap,
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
