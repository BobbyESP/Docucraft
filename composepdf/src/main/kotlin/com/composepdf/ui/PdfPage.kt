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
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PdfPage(
    bitmap: Bitmap?,
    pageIndex: Int,
    aspectRatio: Float,
    isLoading: Boolean,
    showLoadingIndicator: Boolean,
    currentZoom: Float, // Necesitamos el zoom actual para escalar tiles viejos
    fitMode: FitMode = FitMode.WIDTH,
    widthFraction: Float = 1f,
    colorFilter: ColorFilter? = null,
    modifier: Modifier = Modifier,
    tiles: Map<String, Bitmap> = emptyMap()
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
                // 1. Dibujar base siempre (fondo)
                drawImage(
                    image = bitmap.asImageBitmap(),
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    colorFilter = colorFilter
                )

                // 2. Dibujar tiles con re-escalado dinámico
                tiles.forEach { (key, tileBitmap) ->
                    if (key.startsWith("${pageIndex}_")) {
                        val parts = key.split("_")
                        if (parts.size >= 6) {
                            val tileL = parts[1].toInt()
                            val tileT = parts[2].toInt()
                            val tileR = parts[3].toInt()
                            val tileB = parts[4].toInt()
                            val tileZoom = parts[5].toFloat()

                            // Factor de escala entre el momento en que se renderizó el tile y el ahora
                            val scale = currentZoom / tileZoom
                            
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
