/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.components.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftShapeDefaults
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme

/**
 * A titled group of settings. The title sits above the group rather than inside a card around it,
 * and the items are separated by the segmented gap instead of dividers: the page showing between
 * them is what lets each item morph its own corners when pressed.
 *
 * Give each item its place in the group with [DocucraftShapeDefaults.segmentedListItemShapes].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsCategory(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            content = content,
        )
    }
}

@PreviewLightDark
@Composable
private fun SettingsCategoryPreview() {
    DocucraftTheme {
        Surface {
            SettingsCategory(title = "Display") {
                SettingSwitch(
                    title = "Dynamic coloring",
                    supportingText = "Colors from your wallpaper",
                    icon = Icons.Rounded.Palette,
                    isChecked = true,
                    onCheckedChange = {},
                    shapes = DocucraftShapeDefaults.segmentedListItemShapes(index = 0, count = 2),
                )
                SettingSwitch(
                    title = "Night mode",
                    supportingText = "Unavailable while the above is off",
                    icon = Icons.Rounded.DarkMode,
                    isChecked = false,
                    onCheckedChange = {},
                    enabled = false,
                    shapes = DocucraftShapeDefaults.segmentedListItemShapes(index = 1, count = 2),
                )
            }
        }
    }
}
