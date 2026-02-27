package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.sheet.DocumentActionSheetSkeleton
import com.bobbyesp.docucraft.util.MockData

private const val TITLE_MAX_LENGTH = 60
private const val DESCRIPTION_MAX_LENGTH = 200

// ---------------------------------------------------------------------------
// Shared state holder — survives recompositions, rotations and Sheet↔Dialog
// transitions because it lives in the parent via rememberSaveable.
// ---------------------------------------------------------------------------
@Stable
class EditDocumentState(
    initialTitle: String,
    initialDescription: String,
) {
    var title by mutableStateOf(initialTitle)
    var description by mutableStateOf(initialDescription)

    val isTitleError: Boolean get() = title.length > TITLE_MAX_LENGTH
    val isDescriptionError: Boolean get() = description.length > DESCRIPTION_MAX_LENGTH
    val canConfirm: Boolean get() = !isTitleError && !isDescriptionError

    companion object {
        fun saver(): Saver<EditDocumentState, *> = listSaver(
            save = { listOf(it.title, it.description) },
            restore = { EditDocumentState(it[0], it[1]) },
        )
    }
}

@Composable
fun rememberEditDocumentState(doc: ScannedDocument): EditDocumentState =
    rememberSaveable(doc.id, saver = EditDocumentState.saver()) {
        EditDocumentState(
            initialTitle = doc.title.orEmpty(),
            initialDescription = doc.description.orEmpty(),
        )
    }

// ---------------------------------------------------------------------------
// Sheet variant
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditDocumentDetailsSheet(
    doc: ScannedDocument,
    onPopDialog: () -> Unit,
    onConfirmEdit: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    state: EditDocumentState = rememberEditDocumentState(doc),
) {
    val focusManager = LocalFocusManager.current

    // Wrap derivedStateOf so reads inside the lambda are tracked correctly
    val canConfirm by remember { derivedStateOf { state.canConfirm } }

    DocumentActionSheetSkeleton(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(onTap = { focusManager.clearFocus() })
        },
        icon = Icons.Rounded.Edit,
        headingTitle = stringResource(R.string.doc_modify_details),
        content = {
            EditDocumentDetailsContent(
                modifier = Modifier.padding(12.dp),
                state = state,
                onTitleChange = { state.title = it },
                onDescriptionChange = { state.description = it },
            )
        },
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onPopDialog,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.cancel))
                }

                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onPopDialog()
                        onConfirmEdit(state.title.trim(), state.description.trim())
                    },
                    shapes = ButtonDefaults.shapes(),
                    enabled = canConfirm,
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
        },
    )
}

// ---------------------------------------------------------------------------
// AlertDialog variant
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditDocumentDetailsDialog(
    doc: ScannedDocument,
    onDismiss: () -> Unit,
    onConfirmEdit: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    state: EditDocumentState = rememberEditDocumentState(doc),
) {
    val focusManager = LocalFocusManager.current
    val canConfirm by remember { derivedStateOf { state.canConfirm } }

    AlertDialog(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(onTap = { focusManager.clearFocus() })
        },
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = stringResource(R.string.doc_modify_details),
            )
        },
        title = {
            Text(
                text = stringResource(R.string.doc_modify_details),
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier,
            ) {
                item {
                    EditDocumentDetailsContent(
                        state = state,
                        onTitleChange = { state.title = it },
                        onDescriptionChange = { state.description = it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onConfirmEdit(state.title.trim(), state.description.trim())
                },
                enabled = canConfirm,
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

// ---------------------------------------------------------------------------
// Shared content
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EditDocumentDetailsContent(
    state: EditDocumentState,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.doc_modify_details_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        LimitedTextField(
            value = state.title,
            onValueChange = onTitleChange,
            label = stringResource(R.string.title),
            maxLength = TITLE_MAX_LENGTH,
            isError = state.isTitleError,
            supportingText = if (state.isTitleError) stringResource(R.string.field_too_long) else null,
            imeAction = ImeAction.Next,
        )

        LimitedTextField(
            value = state.description,
            onValueChange = onDescriptionChange,
            label = stringResource(R.string.description),
            maxLength = DESCRIPTION_MAX_LENGTH,
            isError = state.isDescriptionError,
            supportingText = if (state.isDescriptionError) stringResource(R.string.field_too_long) else null,
            imeAction = ImeAction.Done,
            singleLine = false,
            minLines = 2,
        )
    }
}

// ---------------------------------------------------------------------------
// LimitedTextField & CharacterCounter — unchanged
// ---------------------------------------------------------------------------

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
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            isError = isError,
            singleLine = singleLine,
            minLines = minLines,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                autoCorrectEnabled = true,
                imeAction = imeAction,
            ),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                AnimatedVisibility(
                    visible = value.isNotEmpty(),
                    enter = fadeIn() + slideInVertically { -it / 2 },
                    exit = fadeOut() + slideOutVertically { -it / 2 },
                ) {
                    IconButton(
                        onClick = { onValueChange("") },
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = if (isError)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Clear,
                            contentDescription = stringResource(R.string.clear_field),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                targetState = supportingText,
                transitionSpec = {
                    (fadeIn() + slideInVertically { -it / 2 }) togetherWith
                        (fadeOut() + slideOutVertically { it / 2 })
                },
                label = "supporting_text",
            ) { text ->
                Text(
                    text = text.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isError)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            CharacterCounter(
                current = value.length,
                max = maxLength,
                isError = isError,
            )
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
    val color = if (isError)
        MaterialTheme.colorScheme.error
    else
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

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
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (isError) FontWeight.Bold else FontWeight.Normal,
                    color = color,
                )
            }
        }
        Text(
            text = " / $max",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = color,
        )
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@PreviewLightDark
@Composable
private fun EditDocumentDetailsDialogPreview() {
    DocucraftTheme {
        EditDocumentDetailsDialog(
            doc = MockData.Documents.documentsList.first(),
            onDismiss = {},
            onConfirmEdit = { _, _ -> },
        )
    }
}

@PreviewLightDark
@Composable
private fun EditDocumentDetailsSheetPreview() {
    DocucraftTheme {
        EditDocumentDetailsSheet(
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow),
            doc = MockData.Documents.documentsList.first(),
            onPopDialog = {},
            onConfirmEdit = { _, _ -> },
        )
    }

}
