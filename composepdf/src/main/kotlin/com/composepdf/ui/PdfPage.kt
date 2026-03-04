package com.composepdf.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.composepdf.state.FitMode
import com.composepdf.state.PdfViewerState
import kotlin.math.roundToInt

/**
 * Composable for rendering a single PDF page with its high-resolution tiles.
 * 
 * Optimized to avoid recomposition: it takes the [state] object and reads 
 * dynamic values (zoom, tiles) only during the Draw phase of the [Canvas].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PdfPage(
    state: PdfViewerState,
    bitmap: Bitmap?,
    pageIndex: Int,
    aspectRatio: Float,
    showLoadingIndicator: Boolean,
    fitMode: FitMode = FitMode.WIDTH,
    widthFraction: Float = 1f,
    colorFilter: ColorFilter? = null,
    modifier: Modifier = Modifier
) {
    val sizeModifier: Modifier = when (fitMode) {
        FitMode.WIDTH -> Modifier.fillMaxWidth().aspectRatio(aspectRatio.coerceAtLeast(0.1f))
        FitMode.HEIGHT -> Modifier.fillMaxHeight().aspectRatio(aspectRatio.coerceAtLeast(0.1f), matchHeightConstraintsFirst = true)
        FitMode.BOTH -> Modifier.fillMaxWidth().aspectRatio(aspectRatio.coerceAtLeast(0.1f))
        FitMode.PROPORTIONAL -> Modifier.fillMaxWidth(widthFraction.coerceIn(0.01f, 1f)).aspectRatio(aspectRatio.coerceAtLeast(0.1f))
    }

    Box(
        modifier = modifier.then(sizeModifier).background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Read current state values. Since this is inside DrawScope, 
                // changes to state.zoom or state.tileRevision only trigger a Redraw, NOT a Recomposition.
                val zoom = state.zoom
                val revision = state.tileRevision // Observe revision to trigger redraw when tiles arrive
                val tiles = state.getAllTiles()

                // 1. Draw base low-resolution bitmap
                drawImage(
                    image = bitmap.asImageBitmap(),
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    colorFilter = colorFilter
                )

                // 2. Draw high-resolution tiles on top with dynamic scaling
                tiles.forEach { (key, tileBitmap) ->
                    if (key.startsWith("${pageIndex}_")) {
                        val parts = key.split("_")
                        if (parts.size >= 6) {
                            val tileL = parts[1].toInt()
                            val tileT = parts[2].toInt()
                            val tileR = parts[3].toInt()
                            val tileB = parts[4].toInt()
                            val tileZoom = parts[5].toFloat()

                            // Scale factor between the tile's native zoom and current viewport zoom
                            val scale = zoom / tileZoom
                            
                            val drawL = (tileL * scale).roundToInt()
                            val drawT = (tileT * scale).roundToInt()
                            val drawW = ((tileR - tileL) * scale).roundToInt()
                            val drawH = ((tileB - tileT) * scale).roundToInt()

                            drawImage(
                                image = tileBitmap.asImageBitmap(),
                                dstOffset = IntOffset(drawL, drawT),
                                dstSize = IntSize(drawW, drawH),
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

/** Placeholder shown when the base page is not yet available. */
@Composable
internal fun PagePlaceholder(
    aspectRatio: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth().aspectRatio(aspectRatio.coerceAtLeast(0.1f)).background(Color(0xFFF5F5F5)),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 4.dp
        )
    }
}
