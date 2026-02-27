package com.bobbyesp.docucraft.core.presentation.components.divider

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import com.bobbyesp.docucraft.core.presentation.components.divider.defaults.WavyDividerColors
import com.bobbyesp.docucraft.core.presentation.components.divider.defaults.WavyDividerDefaults
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme

@Composable
fun WavyDivider(
    modifier: Modifier = Modifier,
    colors: WavyDividerColors = WavyDividerDefaults.colors(),
    strokeWidth: Dp = WavyDividerDefaults.StrokeWidth,
    amplitude: Dp = WavyDividerDefaults.Amplitude,
    waveLength: Dp = WavyDividerDefaults.WaveLength,
) {
    val density = LocalDensity.current
    val strokePx = with(density) { strokeWidth.toPx() }
    val amplitudePx = with(density) { amplitude.toPx() }
    val waveLengthPx = with(density) { waveLength.toPx() }

    val color by colors.color()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(amplitude * 2)
    ) {
        val width = size.width
        val centerY = size.height / 2

        clipRect(right = width) {
            val path = Path()
            path.moveTo(0f, centerY)

            var currentX = 0f

            while (currentX < width) {
                path.relativeQuadraticTo(
                    waveLengthPx / 4,
                    -amplitudePx,
                    waveLengthPx / 2,
                    0f
                )

                path.relativeQuadraticTo(
                    waveLengthPx / 4,
                    amplitudePx,
                    waveLengthPx / 2,
                    0f
                )

                currentX += waveLengthPx
            }

            drawPath(
                path = path,
                color = color,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun WavyDividerPreview() {
    DocucraftTheme() {
        WavyDivider()
    }
}