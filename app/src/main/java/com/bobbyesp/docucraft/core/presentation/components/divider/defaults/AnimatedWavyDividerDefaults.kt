package com.bobbyesp.docucraft.core.presentation.components.divider.defaults

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object AnimatedWavyDividerDefaults {

    @Composable
    fun colors(
        color: Color = MaterialTheme.colorScheme.primary,
    ): WavyDividerColors {
        return WavyDividerColors(containerColor = color)
    }

    val StrokeWidth: Dp = 3.dp
    val Amplitude: Dp = 4.dp
    val WaveLength: Dp = 48.dp
    val Duration: Int = 2000

    val Height: Dp
        get() = Amplitude * 3
}