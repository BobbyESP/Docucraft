/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.components.details

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.designsystem.R as DesignsystemR
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme

private const val TITLE_MAX_LENGTH = 60

private const val DESCRIPTION_MAX_LENGTH = 200

/**
 * The two fields the user writes of a document, each with the room it has left. Shared by the
 * overlay that edits a document and the review of a new scan, so that a title is typed the same way
 * in both.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditDocumentDetailsContent(
    state: EditDocumentUiState,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LimitedTextField(
            value = state.title,
            onValueChange = onTitleChange,
            label = stringResource(DesignsystemR.string.title),
            maxLength = TITLE_MAX_LENGTH,
            isError = state.isTitleError,
            supportingText =
                if (state.isTitleError) stringResource(R.string.field_too_long) else null,
            imeAction = ImeAction.Next,
        )

        LimitedTextField(
            value = state.description,
            onValueChange = onDescriptionChange,
            label = stringResource(DesignsystemR.string.description),
            maxLength = DESCRIPTION_MAX_LENGTH,
            isError = state.isDescriptionError,
            supportingText =
                if (state.isDescriptionError) stringResource(R.string.field_too_long) else null,
            imeAction = ImeAction.Done,
            singleLine = false,
            minLines = 2,
        )
    }
}

@Composable
private fun LimitedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    maxLength: Int,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
    imeAction: ImeAction = ImeAction.Default,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color =
            if (isError) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
            else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (isError) BorderStroke(1.dp, MaterialTheme.colorScheme.error) else null,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TextField(
                value = value,
                onValueChange = { if (it.length <= maxLength) onValueChange(it) },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                },
                isError = isError,
                singleLine = singleLine,
                minLines = minLines,
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        errorContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        errorIndicatorColor = Color.Transparent,
                    ),
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        autoCorrectEnabled = true,
                        imeAction = imeAction,
                    ),
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    AnimatedVisibility(
                        visible = value.isNotEmpty(),
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut(),
                    ) {
                        IconButton(onClick = { onValueChange("") }) {
                            Icon(
                                imageVector = Icons.Rounded.Clear,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint =
                                    if (isError) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(targetState = supportingText, label = "supp_text") { text ->
                    Text(
                        text = text.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            if (isError) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                CharacterCounter(current = value.length, max = maxLength, isError = isError)
            }
        }
    }
}

@Composable
private fun CharacterCounter(
    current: Int,
    max: Int,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    val color =
        if (isError) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        val currentStr = current.toString()
        currentStr.forEach { char ->
            AnimatedContent(
                targetState = char,
                transitionSpec = {
                    slideInVertically { it } togetherWith slideOutVertically { -it }
                },
                label = "counter_char",
            ) { c ->
                Text(
                    text = c.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = DocucraftTheme.typography.monospace,
                    fontWeight = if (isError) FontWeight.Bold else FontWeight.Normal,
                    color = color,
                )
            }
        }
        Text(
            text = " / $max",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = DocucraftTheme.typography.monospace,
            color = color,
        )
    }
}
