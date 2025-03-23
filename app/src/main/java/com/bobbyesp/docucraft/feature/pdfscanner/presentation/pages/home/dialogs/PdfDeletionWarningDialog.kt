package com.bobbyesp.docucraft.feature.pdfscanner.presentation.pages.home.dialogs

import android.text.format.Formatter.formatFileSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.feature.pdfscanner.domain.model.ScannedPdf

@Composable
fun PdfDeletionWarningDialog(
    modifier: Modifier = Modifier,
    scannedPdf: ScannedPdf,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val context = LocalContext.current

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.DeleteForever,
                contentDescription = stringResource(R.string.delete_pdf),
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text(text = stringResource(R.string.delete_pdf), fontWeight = FontWeight.SemiBold)
        },
        text = {
            Text(
                text =
                    stringResource(
                        R.string.delete_pdf_warning_message,
                        scannedPdf.title ?: scannedPdf.filename,
                        scannedPdf.pageCount.toString(),
                        formatFileSize(context, scannedPdf.fileSize),
                    )
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
            ) {
                Text(text = stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.cancel)) }
        },
    )
}
