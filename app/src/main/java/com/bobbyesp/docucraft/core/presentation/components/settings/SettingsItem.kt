package com.bobbyesp.docucraft.core.presentation.components.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsItem(item: SettingsItem, modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium
            )
        },
        supportingContent = {
            Text(
                text = item.supportingText,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        leadingContent = {
            Icon(
                imageVector = item.icon,
                contentDescription = null
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = modifier.clickable(onClick = item.onClick)
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsGroup(items: ImmutableList<SettingsItem>, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.fastForEachIndexed { index, item ->
            SettingsItem(
                item = item,
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(
                            when {
                                items.size == 1 -> DocucraftShapeDefaults.independentListItemShape
                                index == 0 -> DocucraftShapeDefaults.topListItemShape
                                index == items.lastIndex -> DocucraftShapeDefaults.bottomListItemShape
                                else -> MaterialTheme.shapes.medium
                            }
                        ),
            )
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
        SettingsGroup(
            items =
                persistentListOf(
                    SettingsItem(
                        title = "Title",
                        supportingText = "Supporting Text",
                        icon = Icons.Rounded.Settings,
                        onClick = {},
                    ),
                    SettingsItem(
                        title = "Title",
                        supportingText = "Supporting Text",
                        icon = Icons.Rounded.Settings,
                        onClick = {},
                    ),
                    SettingsItem(
                        title = "Title",
                        supportingText = "Supporting Text",
                        icon = Icons.Rounded.Settings,
                        onClick = {},
                    ),
                    SettingsItem(
                        title = "Title",
                        supportingText = "Supporting Text",
                        icon = Icons.Rounded.Settings,
                        onClick = {},
                    ),
                    SettingsItem(
                        title = "Title",
                        supportingText = "Supporting Text",
                        icon = Icons.Rounded.Settings,
                        onClick = {},
                    ),
                    SettingsItem(
                        title = "Appearance",
                        supportingText = "Theme and typography",
                        icon = Icons.Rounded.Settings,
                        onClick = {},
                    ),
                )
        )
    }
}
