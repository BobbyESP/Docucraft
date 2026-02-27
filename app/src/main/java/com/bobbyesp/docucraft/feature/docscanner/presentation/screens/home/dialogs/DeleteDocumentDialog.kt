package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.dialogs

import android.text.format.Formatter.formatFileSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.glance.layout.Spacer
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
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.doc_delete),
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )

                    Spacer(modifier = Modifier.padding(end = 8.dp))

                    Text(stringResource(R.string.delete))
                }

                OutlinedButton(
                    onClick = onDismiss,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().sizeIn(
                        minHeight = ButtonDefaults.MinHeight * 1.5f
                    )
                ) {
                    Text(stringResource(R.string.cancel))
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DeleteDocumentContent(
    scannedDocument: ScannedDocument,
    modifier: Modifier = Modifier,
) {
    val documentTitle = scannedDocument.title ?: scannedDocument.filename
    val text = stringResource(R.string.doc_delete_confirmation, documentTitle)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = buildAnnotatedString {
                val start = text.indexOf(documentTitle)
                val end = start + documentTitle.length

                append(text)

                if (start >= 0) {
                    addStyle(
                        style = SpanStyle(fontWeight = FontWeight.Bold),
                        start = start,
                        end = end
                    )
                }
            },
            textAlign = TextAlign.Center
        )

        Text(
            modifier = Modifier.alpha(0.66f),
            text = stringResource(R.string.doc_delete_warning),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}
