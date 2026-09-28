/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.components.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftShapeDefaults
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme

/**
 * A setting that is on or off, as an expressive segmented list item: the whole row flips it, and
 * its corners round further while pressed. Disabled, the row keeps its container and dims its
 * content, so the setting stays readable while it cannot be changed.
 *
 * Not the list's toggleable overload: that one paints a checked row in the selected color and
 * shape, and announces it as a checkbox. Here only the switch shows the state, and the row is
 * announced as the switch it is.
 *
 * @param shapes where the item sits in its group; see
 *   [DocucraftShapeDefaults.segmentedListItemShapes].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingSwitch(
    title: String,
    supportingText: String,
    icon: ImageVector,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shapes: ListItemShapes = DocucraftShapeDefaults.segmentedListItemShapes(index = 0, count = 1),
) {
    SegmentedListItem(
        onClick = { onCheckedChange(!isChecked) },
        shapes = shapes,
        modifier =
            modifier.semantics {
                role = Role.Switch
                toggleableState = ToggleableState(isChecked)
            },
        enabled = enabled,
        leadingContent = { SettingsItemIcon(icon = icon, enabled = enabled) },
        trailingContent = {
            // The row is the control; the switch only shows its state.
            Switch(
                checked = isChecked,
                onCheckedChange = null,
                enabled = enabled,
                thumbContent =
                    if (isChecked) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize),
                            )
                        }
                    } else {
                        null
                    },
            )
        },
        supportingContent = { Text(text = supportingText) },
        colors = SettingsItemDefaults.colors(),
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLargeEmphasized)
    }
}

@PreviewLightDark
@Composable
private fun SettingsSwitchPreview() {
    DocucraftTheme {
        Surface {
            SettingSwitch(
                title = "Title",
                supportingText = "Supporting Text",
                icon = Icons.Rounded.Settings,
                isChecked = true,
                onCheckedChange = {},
            )
        }
    }
}
