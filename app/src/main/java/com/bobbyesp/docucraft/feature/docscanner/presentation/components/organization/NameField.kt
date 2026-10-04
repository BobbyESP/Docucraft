/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.components.organization

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.folders.NameError

/**
 * The field a folder or a tag is named in. What is wrong with the name is said under it, in place:
 * it is something the user typed, not a failure.
 *
 * @param takenMessage What to say when another one has the name, which differs by what is named.
 */
@Composable
fun NameField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: NameError?,
    takenMessage: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= NameMaxLength) onValueChange(it) },
        modifier = modifier.fillMaxWidth(),
        label = { Text(text = label) },
        singleLine = true,
        isError = error != null,
        supportingText =
            error?.let {
                {
                    Text(
                        text =
                            when (it) {
                                NameError.Empty -> stringResource(R.string.name_empty)
                                NameError.Taken -> takenMessage
                            }
                    )
                }
            },
        shape = MaterialTheme.shapes.large,
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
    )
}

private const val NameMaxLength = 40
