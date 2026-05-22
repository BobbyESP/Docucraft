package com.bobbyesp.docucraft.core.presentation.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingSwitch(
    title: String,
    supportingText: String,
    icon: ImageVector,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(text = title) },
        supportingContent = { Text(text = supportingText) },
        leadingContent = { Icon(imageVector = icon, contentDescription = null) },
        trailingContent = {
            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .clickable { onCheckedChange(!isChecked) }
    )
}

@PreviewLightDark
@Composable
private fun SettingsSwitchPreview() {
    DocucraftTheme {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(16.dp)
        ) {
            SettingSwitch(
                title = "Title",
                supportingText = "Supporting Text",
                icon = Icons.Rounded.Settings,
                isChecked = true,
                onCheckedChange = {}
            )
        }
    }
}
