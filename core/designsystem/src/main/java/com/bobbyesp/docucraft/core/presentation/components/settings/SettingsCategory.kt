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
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.bobbyesp.docucraft.core.presentation.components.SectionHeader
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftShapeDefaults
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme

/**
 * A titled group of settings. The title is the section header every list of the app has, above the
 * group rather than inside a card around it, and the items are separated by the segmented gap
 * instead of dividers: the page showing between them is what lets each item morph its own corners
 * when pressed.
 *
 * It keeps its own margins, the header's being wider than the items': place it edge to edge, and
 * give a setting that stands outside a category [SettingsItemDefaults.HorizontalMargin].
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
        SectionHeader(title = title)
        Column(
            modifier = Modifier.padding(horizontal = SettingsItemDefaults.HorizontalMargin),
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
