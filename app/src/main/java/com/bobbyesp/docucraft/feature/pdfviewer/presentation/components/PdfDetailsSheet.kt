/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.components

import android.content.Context
import android.provider.OpenableColumns
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.feature.shared.domain.BasicDocument
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Modal bottom sheet showing metadata about the document currently open in the viewer: filename,
 * page count, file size and description.
 *
 * @param documentInfo The document being described.
 * @param pageCount Total page count reported by the viewer state.
 * @param onDismiss Invoked when the sheet is dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PdfDetailsSheet(
    documentInfo: BasicDocument,
    pageCount: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)

    val fileSize by
        produceState<String?>(initialValue = null, documentInfo.uri) {
            value = withContext(Dispatchers.IO) { resolveFileSize(context, documentInfo.uri) }
        }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = modifier) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(
                        bottom =
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                                24.dp
                    ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.document_details),
                style = MaterialTheme.typography.headlineSmallEmphasized,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            DetailRow(
                label = stringResource(R.string.name),
                value = documentInfo.title ?: documentInfo.filename,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            DetailRow(
                label = stringResource(R.string.page_count),
                value = if (pageCount > 0) pageCount.toString() else "—",
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            DetailRow(label = stringResource(R.string.file_size), value = fileSize ?: "—")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            DetailRow(
                label = stringResource(R.string.description),
                value = documentInfo.description ?: stringResource(R.string.no_description),
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.6f),
        )
    }
}

/** Resolves a human-readable file size for either a `content://` or a file-based URI. */
private fun resolveFileSize(context: Context, uriString: String): String? {
    val uri = uriString.toUri()
    val bytes: Long? =
        when (uri.scheme) {
            "content" ->
                runCatching {
                        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (
                                sizeIndex >= 0 && cursor.moveToFirst() && !cursor.isNull(sizeIndex)
                            ) {
                                cursor.getLong(sizeIndex)
                            } else null
                        }
                    }
                    .getOrNull()

            else ->
                runCatching { File(uri.path ?: uriString).length().takeIf { it > 0 } }.getOrNull()
        }

    return bytes?.let { Formatter.formatShortFileSize(context, it) }
}
