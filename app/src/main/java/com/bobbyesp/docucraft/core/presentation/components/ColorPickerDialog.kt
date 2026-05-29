/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(
    initialColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presetColors =
        listOf(
            Color(0xFF6200EE), // Default
            Color(0xFFF44336), // Red
            Color(0xFFE91E63), // Pink
            Color(0xFF9C27B0), // Purple
            Color(0xFF673AB7), // Deep Purple
            Color(0xFF3F51B5), // Indigo
            Color(0xFF2196F3), // Blue
            Color(0xFF03A9F4), // Light Blue
            Color(0xFF00BCD4), // Cyan
            Color(0xFF009688), // Teal
            Color(0xFF4CAF50), // Green
            Color(0xFF8BC34A), // Light Green
            Color(0xFFCDDC39), // Lime
            Color(0xFFFFEB3B), // Yellow
            Color(0xFFFFC107), // Amber
            Color(0xFFFF9800), // Orange
            Color(0xFFFF5722), // Deep Orange
            Color(0xFF795548), // Brown
            Color(0xFF9E9E9E), // Grey
            Color(0xFF607D8B), // Blue Grey
        )

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(stringResource(R.string.seed_color)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    presetColors.forEach { color ->
                        val isSelected = color == initialColor
                        Box(
                            modifier =
                                Modifier.size(48.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(
                                        if (isSelected)
                                            Modifier.border(
                                                2.dp,
                                                MaterialTheme.colorScheme.onSurface,
                                                CircleShape,
                                            )
                                        else Modifier
                                    )
                                    .clickable {
                                        onColorSelected(color)
                                        onDismiss()
                                    },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
