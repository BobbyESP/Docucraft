/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.components.settings

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
 * that round further while pressed, and colors and type taken from the list tokens rather than set
 * here, so dynamic color and contrast levels reach it untouched.
 *
 * @param shapes where the item sits in its group; see
 *   [DocucraftShapeDefaults.segmentedListItemShapes].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsItem(
    item: SettingsItem,
    modifier: Modifier = Modifier,
    shapes: ListItemShapes = DocucraftShapeDefaults.segmentedListItemShapes(index = 0, count = 1),
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
        colors = SettingsItemDefaults.colors(),
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
                shapes =
                    DocucraftShapeDefaults.segmentedListItemShapes(
                        index = index,
                        count = items.size,
                    ),
            )
        }
    }
}

/** What every item of a settings list shares, whatever control it carries. */
object SettingsItemDefaults {

    /**
     * The grouped surface's container, kept while disabled: the list's own disabled container is
     * the page's color, and a disabled item would vanish from its group instead of looking
     * unavailable.
     */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun colors(): ListItemColors =
        ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        )
}

/**
 * The icon on a tonal disc, the paired container and content roles keeping it legible. Disabled, it
 * takes Material's disabled colors, eased so it fades along with the rest of the item.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsItemIcon(icon: ImageVector, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    val spec = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val containerColor by
        animateColorAsState(
            targetValue =
                if (enabled) colors.primaryContainer
                else colors.onSurface.copy(alpha = DisabledContainerAlpha),
            animationSpec = spec,
            label = "SettingsItemIconContainer",
        )
    val contentColor by
        animateColorAsState(
            targetValue =
                if (enabled) colors.onPrimaryContainer
                else colors.onSurface.copy(alpha = DisabledContentAlpha),
            animationSpec = spec,
            label = "SettingsItemIconContent",
        )

    Surface(
        modifier = modifier.size(40.dp),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp))
        }
    }
}

/** Material's opacities for a disabled component's container and content. */
private const val DisabledContainerAlpha = 0.12f
private const val DisabledContentAlpha = 0.38f

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
