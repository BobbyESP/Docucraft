package com.bobbyesp.docucraft.core.presentation.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList

@Composable
fun <T : SettingOption> SettingOptionsRow(
    title: String,
    options: ImmutableList<T>,
    modifier: Modifier = Modifier,
    optionContent: @Composable (T) -> Unit,
) {
    Column(
        modifier =
            modifier
                .clip(ShapeDefaults.ExtraLarge)
                .background(color = MaterialTheme.colorScheme.surfaceContainer)
                .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 20.dp),
        )

        LazyRow {
            item(contentType = "spacing") { Spacer(modifier = Modifier.width(8.dp)) }
            items(items = options, key = { it.title }, contentType = { "option" }) { option ->
                optionContent(option)
            }
            item(contentType = "spacing") { Spacer(modifier = Modifier.width(8.dp)) }
        }
    }
}

sealed class SettingOption(val title: String, val onSelection: () -> Unit)
