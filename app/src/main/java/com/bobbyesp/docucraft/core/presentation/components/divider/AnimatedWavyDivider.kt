package com.bobbyesp.docucraft.core.presentation.components.divider

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.bobbyesp.docucraft.core.presentation.components.divider.defaults.AnimatedWavyDividerDefaults
import com.bobbyesp.docucraft.core.presentation.components.divider.defaults.WavyDividerColors
import kotlin.math.PI
import kotlin.math.sin

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnimatedWavyDivider(
    modifier: Modifier = Modifier,
    colors: WavyDividerColors = AnimatedWavyDividerDefaults.colors(),
    strokeWidth: Dp = AnimatedWavyDividerDefaults.StrokeWidth,
    amplitude: Dp = AnimatedWavyDividerDefaults.Amplitude,
    waveLength: Dp = AnimatedWavyDividerDefaults.WaveLength,
    duration: Int = AnimatedWavyDividerDefaults.Duration,
) {
    val density = LocalDensity.current

    val strokePx = with(density) { strokeWidth.toPx() }
    val amplitudePx = with(density) { amplitude.toPx() }
    val waveLengthPx = with(density) { waveLength.toPx() }

    val color by colors.color()

    val infiniteTransition = rememberInfiniteTransition(label = "wave")

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = duration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(AnimatedWavyDividerDefaults.Height)
    ) {
        val width = size.width
        val centerY = size.height / 2

        clipRect(right = width) {
            val path = Path().apply {

                val startY = centerY + (amplitudePx * sin(phase.toDouble())).toFloat()
                moveTo(0f, startY)

                var x = 0f
                while (x <= width) {
                    val y = centerY + (amplitudePx * sin((2 * PI * x / waveLengthPx + phase))).toFloat()
                    lineTo(x, y)
                    x += 2f
                }
            }

            drawPath(
                path = path,
                color = color,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }
    }
}