/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.components.settings

import androidx.annotation.IntRange
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftShapeDefaults
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme

@Composable
fun SettingSlider(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinish: () -> Unit,
    modifier: Modifier = Modifier,
    valueToShow: String? = null,
    @IntRange steps: Int = 0,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    Column(
        modifier =
            modifier
                .clip(DocucraftShapeDefaults.cardShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = valueToShow ?: value.toInt().toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Slider(
            state =
                rememberSliderState(
                    value = value,
                    steps = steps,
                    trackRange = valueRange,
                ),
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = true,
            onValueChangeFinished = onValueChangeFinish,
            colors = SliderDefaults.colors(),
        )
    }
}

@Preview
@Composable
private fun SettingSliderPreview() {
    DocucraftTheme {
        SettingSlider(
            title = "Title",
            value = 0.5f,
            onValueChange = {},
            onValueChangeFinish = {},
            valueToShow = "50%",
            valueRange = 0f..1f,
            steps = 4,
        )
    }
}
