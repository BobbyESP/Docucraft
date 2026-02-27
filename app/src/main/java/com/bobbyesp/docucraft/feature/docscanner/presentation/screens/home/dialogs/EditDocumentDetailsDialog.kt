package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument

@Composable
fun EditDocumentDetailsSheet(
    doc: ScannedDocument,
    onPopDialog: () -> Unit,
    onConfirmEdit: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentTitle by rememberSaveable(doc.id) {
        mutableStateOf(doc.title.orEmpty())
    }
    var currentDescription by rememberSaveable(doc.id) {
        mutableStateOf(doc.description.orEmpty())
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
        ) {
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = stringResource(R.string.doc_modify_details),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        EditDocumentDetailsContent(
            title = currentTitle,
            description = currentDescription,
            onTitleChange = { currentTitle = it },
            onDescriptionChange = { currentDescription = it },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onPopDialog,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.cancel))
            }

            Button(
                onClick = {
                    onPopDialog()
                    onConfirmEdit(currentTitle, currentDescription)
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.confirm))
            }
        }
    }
}

@Composable
fun EditDocumentDetailsDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
    title: String?,
    description: String?,
    modifier: Modifier = Modifier,
) {
    var titleText by rememberSaveable { mutableStateOf(title.orEmpty()) }
    var descriptionText by rememberSaveable { mutableStateOf(description.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.doc_modify_details),
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            EditDocumentDetailsContent(
                title = titleText,
                description = descriptionText,
                onTitleChange = { titleText = it },
                onDescriptionChange = { descriptionText = it },
                modifier = modifier
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(titleText, descriptionText) }
            ) {
                Text(text = stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun EditDocumentDetailsContent(
    title: String,
    description: String,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboardOptions = KeyboardOptions(
        capitalization = KeyboardCapitalization.Sentences,
        autoCorrectEnabled = true,
        imeAction = ImeAction.Next,
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.doc_modify_details_description),
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text(stringResource(R.string.title)) },
            keyboardOptions = keyboardOptions,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            label = { Text(stringResource(R.string.description)) },
            keyboardOptions = keyboardOptions.copy(imeAction = ImeAction.Done),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
