/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.components.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftShapeDefaults
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class SettingsItem(
    val title: String,
    val supportingText: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/**
 * One entry of a settings list, as an expressive segmented list item: its own container, corners
 * that round further while pressed, and colours and type taken from the list tokens rather than set
 * here, so dynamic colour and contrast levels reach it untouched.
 *
 * @param shapes where the item sits in its group; see [settingsItemShapes].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsItem(
    item: SettingsItem,
    modifier: Modifier = Modifier,
    shapes: ListItemShapes = settingsItemShapes(index = 0, count = 1),
) {
    SegmentedListItem(
        onClick = item.onClick,
        shapes = shapes,
        modifier = modifier,
        leadingContent = { SettingsItemIcon(item.icon) },
        trailingContent = {
            Icon(imageVector = Icons.Rounded.ChevronRight, contentDescription = null)
        },
        supportingContent = { Text(text = item.supportingText) },
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
    ) {
        Text(text = item.title, style = MaterialTheme.typography.bodyLargeEmphasized)
    }
}

/**
 * Items of one group, separated by the segmented gap rather than dividers: the page showing between
 * them is the divider, which is what lets each item morph its own corners when pressed.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsGroup(items: ImmutableList<SettingsItem>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        items.fastForEachIndexed { index, item ->
            SettingsItem(
                item = item,
                modifier = Modifier.fillMaxWidth(),
                shapes = settingsItemShapes(index = index, count = items.size),
            )
        }
    }
}

/**
 * The resting shape for the item at [index] of [count], with the list's pressed, focused and
 * hovered shapes on top.
 *
 * Not [ListItemDefaults.segmentedShapes]: its outer corners are the list token's, a step smaller
 * than the [DocucraftShapeDefaults] ones every other grouped list in the app uses.
 */
@Composable
fun settingsItemShapes(index: Int, count: Int): ListItemShapes =
    ListItemDefaults.shapes(
        shape =
            when {
                count == 1 -> DocucraftShapeDefaults.independentListItemShape
                index == 0 -> DocucraftShapeDefaults.topListItemShape
                index == count - 1 -> DocucraftShapeDefaults.bottomListItemShape
                else -> DocucraftShapeDefaults.middleListItemShape
            }
    )

/** The icon on a tonal disc, the paired container and content roles keeping it legible. */
@Composable
private fun SettingsItemIcon(icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(40.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp))
        }
    }
}

@PreviewLightDark
@Composable
private fun SettingsItemPreview() {
    DocucraftTheme {
        SettingsItem(
            item =
                SettingsItem(
                    title = "Title",
                    supportingText = "Supporting Text",
                    icon = Icons.Rounded.Settings,
                    onClick = {},
                )
        )
    }
}

@PreviewLightDark
@Composable
private fun SettingsGroupPreview() {
    DocucraftTheme {
        Surface {
            SettingsGroup(
                items =
                    persistentListOf(
                        SettingsItem(
                            title = "Appearance",
                            supportingText = "Theme and typography",
                            icon = Icons.Rounded.ColorLens,
                            onClick = {},
                        ),
                        SettingsItem(
                            title = "Document viewer",
                            supportingText = "Zoom, layout and page display",
                            icon = Icons.Rounded.Description,
                            onClick = {},
                        ),
                        SettingsItem(
                            title = "Title",
                            supportingText = "Supporting Text",
                            icon = Icons.Rounded.Settings,
                            onClick = {},
                        ),
                    )
            )
        }
    }
}
