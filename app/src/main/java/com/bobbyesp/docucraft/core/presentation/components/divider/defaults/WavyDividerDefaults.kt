package com.bobbyesp.docucraft.core.presentation.components.divider.defaults

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object WavyDividerDefaults {
    @Composable
    fun colors(
        color: Color = MaterialTheme.colorScheme.outlineVariant,
    ): WavyDividerColors {
        return WavyDividerColors(color)
    }

    val StrokeWidth: Dp = 2.dp
    val Amplitude: Dp = 4.dp
    val WaveLength: Dp = 24.dp

    val Height: Dp
        get() = Amplitude * 2
}