/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.components.selectiongroup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme

/**
 * A single-select group of connected toggle buttons.
 *
 * Rendered as a Material 3 *connected* button group: the outer corners of the group are rounded
 * while the corners between members stay tight, so the options read as one control rather than as
 * three loose buttons. The shape morphs on press and on selection, which gives tactile confirmation
 * for a control whose effect usually happens elsewhere on screen.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> SelectionGroupRow(
    options: List<T>,
    selectedOption: T?,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    labelContent: @Composable (T) -> Unit = { Text(it.toString()) },
) {
    Row(
        modifier = modifier.semantics { isTraversalGroup = true },
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, item ->
            val shapes =
                when {
                    options.size == 1 -> ToggleButtonDefaults.shapes()
                    index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    index == options.lastIndex ->
                        ButtonGroupDefaults.connectedTrailingButtonShapes()

                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                }

            ToggleButton(
                checked = item == selectedOption,
                onCheckedChange = { isChecked -> if (isChecked) onOptionSelected(item) },
                shapes = shapes,
                // ToggleButton's default height is 40dp (36dp with precision pointers), both below
                // the 48dp touch-target floor.
                modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
                content = { labelContent(item) },
            )
        }
    }
}

@Preview
@Composable
private fun SelectionGroupRowPreview() {
    DocucraftTheme {
        Surface {
            var selectedOption by remember { mutableStateOf("Date") }
            val itemSet = listOf("Date", "Name", "Size")
            SelectionGroupRow(
                options = itemSet,
                selectedOption = selectedOption,
                onOptionSelected = { selectedOption = it },
            )
        }
    }
}
