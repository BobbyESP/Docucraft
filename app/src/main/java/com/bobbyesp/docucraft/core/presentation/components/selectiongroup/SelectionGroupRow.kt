package com.bobbyesp.docucraft.core.presentation.components.selectiongroup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> SelectionGroupRow(
    options: List<T>,
    selectedOption: T?,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    key: ((T) -> Any)? = null,
    labelContent: @Composable (T) -> Unit = { Text(it.toString()) }
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
    ) {
        items(
            items = options,
            key = key
        ) { item ->
            ToggleButton(
                checked = item == selectedOption,
                onCheckedChange = { isChecked ->
                    if (isChecked) {
                        onOptionSelected(item)
                    }
                },
                content = { labelContent(item) }
            )
        }
    }
}

@Preview
@Composable
private fun Preview() {
    DocucraftTheme {
        Surface {
            var selectedOption by remember { mutableStateOf("All") }
            val itemSet = listOf("All", "Downloaded", "Canceled", "Finished")
            SelectionGroupRow(
                options = itemSet,
                selectedOption = selectedOption,
                onOptionSelected = { selectedOption = it }
            )
        }
    }
}

