package com.bobbyesp.docucraft.core.presentation.components.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftShapeDefaults
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class SettingsItem(
    val title: String,
    val supportingText: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsItem(item: SettingsItem, modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .background(color = MaterialTheme.colorScheme.surfaceContainer)
                .combinedClickable(onClick = item.onClick)
                .padding(horizontal = 24.dp, vertical = 16.dp), // maybe delete this padding
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = item.icon,
            tint = MaterialTheme.colorScheme.onSurface,
            contentDescription = null,
        )

        Column {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = item.supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SettingsGroup(items: ImmutableList<SettingsItem>, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.fastForEachIndexed { index, item ->
            SettingsItem(
                item = item,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(
                            when {
                                items.size == 1 -> {
                                    DocucraftShapeDefaults.middleListItemShape
                                }

                                index == 0 -> {
                                    DocucraftShapeDefaults.topListItemShape
                                }

                                index == items.lastIndex -> {
                                    DocucraftShapeDefaults.bottomListItemShape
                                }

                                else -> {
                                    MaterialTheme.shapes.medium
                                }
                            }
                        ),
            )
        }
    }
}

// create previews
@Preview
@Composable
fun SettingsItemPreview() {
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

@Preview
@Composable
fun SettingsGroupPreview() {
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
            )
    )
}
