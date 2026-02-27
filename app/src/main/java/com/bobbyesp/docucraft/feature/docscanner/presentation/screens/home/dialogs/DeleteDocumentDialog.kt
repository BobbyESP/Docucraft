package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.dialogs

import android.text.format.Formatter.formatFileSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.sheet.DocumentActionSheetSkeleton

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DeleteDocumentSheet(
    document: ScannedDocument,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DocumentActionSheetSkeleton(
        modifier = modifier,
        headingTitle = stringResource(R.string.doc_delete),
        icon = Icons.Rounded.DeleteForever,
        iconTint = MaterialTheme.colorScheme.error,
        content = {
            DeleteDocumentContent(
                modifier = Modifier.padding(8.dp),
                scannedDocument = document
            )
        },
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.cancel))
                }

                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.delete))
                }
            }
        }
    )
}

@Composable
fun DeleteDocumentDialog(
    scannedDocument: ScannedDocument,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.DeleteForever,
                contentDescription = stringResource(R.string.doc_delete),
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text(
                text = stringResource(R.string.doc_delete),
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            DeleteDocumentContent(
                scannedDocument = scannedDocument
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
            ) {
                Text(text = stringResource(R.string.delete))
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
private fun DeleteDocumentContent(
    scannedDocument: ScannedDocument,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(
                R.string.doc_delete_warning_message,
                scannedDocument.title ?: scannedDocument.filename,
                scannedDocument.pageCount.toString(),
                formatFileSize(context, scannedDocument.fileSize),
            )
        )
    }
}
