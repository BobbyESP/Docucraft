/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.designsystem.R as DesignsystemR
import com.bobbyesp.docucraft.core.presentation.components.overlay.OverlaySheetSkeleton
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.details.EditDocumentDetailsContent
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.details.EditDocumentUiState
import com.bobbyesp.docucraft.feature.docscanner.presentation.preview.DocumentPreviewData

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditDocumentDetailsSheet(
    state: EditDocumentUiState,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onPopDialog: () -> Unit,
    onConfirmEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current

    OverlaySheetSkeleton(
        modifier =
            modifier.pointerInput(Unit) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            },
        icon = Icons.Rounded.Edit,
        headingTitle = stringResource(R.string.doc_modify_details),
        headingDescription = stringResource(R.string.doc_modify_details_description),
        content = {
            EditDocumentDetailsContent(
                modifier = Modifier.padding(12.dp),
                state = state,
                onTitleChange = onTitleChange,
                onDescriptionChange = onDescriptionChange,
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
                    Text(stringResource(DesignsystemR.string.cancel))
                }

                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onConfirmEdit,
                    shapes = ButtonDefaults.shapes(),
                    enabled = state.canConfirm,
                ) {
                    Text(stringResource(DesignsystemR.string.confirm))
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditDocumentDetailsDialog(
    state: EditDocumentUiState,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirmEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current

    AlertDialog(
        modifier =
            modifier.pointerInput(Unit) {
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
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.doc_modify_details),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.doc_modify_details_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            LazyColumn {
                item {
                    EditDocumentDetailsContent(
                        state = state,
                        onTitleChange = onTitleChange,
                        onDescriptionChange = onDescriptionChange,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirmEdit, enabled = state.canConfirm) {
                Text(stringResource(DesignsystemR.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(DesignsystemR.string.cancel)) }
        },
    )
}

@PreviewLightDark
@Composable
private fun EditDocumentDetailsDialogPreview() {
    val doc = DocumentPreviewData.documents.first()
    DocucraftTheme {
        EditDocumentDetailsDialog(
            state =
                EditDocumentUiState(
                    title = doc.title.orEmpty(),
                    description = doc.description.orEmpty(),
                ),
            onTitleChange = {},
            onDescriptionChange = {},
            onDismiss = {},
            onConfirmEdit = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun EditDocumentDetailsSheetPreview() {
    val doc = DocumentPreviewData.documents.first()
    DocucraftTheme {
        EditDocumentDetailsSheet(
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow),
            state =
                EditDocumentUiState(
                    title = doc.title.orEmpty(),
                    description = doc.description.orEmpty(),
                ),
            onTitleChange = {},
            onDescriptionChange = {},
            onPopDialog = {},
            onConfirmEdit = {},
        )
    }
}
