/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.data.details

import android.content.ContentResolver
import android.content.Context
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.bobbyesp.docucraft.feature.pdfviewer.domain.details.DocumentFacts
import com.bobbyesp.docucraft.feature.pdfviewer.domain.details.DocumentFactsReader
import com.bobbyesp.scanner.ContentRef
import com.composepdf.PdfRenderers
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Size from the provider (or the file system), page count from the platform renderer. Either can
 * fail on a file another app handed over, and neither failure is worth more than a dash in the
 * details, so both come back `null` rather than throwing.
 */
class AndroidDocumentFactsReader(private val context: Context) : DocumentFactsReader {

    override suspend fun read(document: ContentRef): DocumentFacts =
        withContext(Dispatchers.IO) {
            val uri = document.value.toUri()
            DocumentFacts(sizeBytes = runCatching { size(uri) }.getOrNull(), pageCount = pages(uri))
        }

    private fun size(uri: android.net.Uri): Long? =
        when (uri.scheme) {
            ContentResolver.SCHEME_CONTENT ->
                context.contentResolver
                    .query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
                    ?.use { cursor ->
                        val column = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (column >= 0 && cursor.moveToFirst() && !cursor.isNull(column)) {
                            cursor.getLong(column)
                        } else null
                    }

            else -> uri.path?.let(::File)?.length()
        }?.takeIf { it > 0 }

    private fun pages(uri: android.net.Uri): Int? {
        val descriptor =
            runCatching { context.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
                ?: return null
        // The renderer owns the descriptor from here on and closes it with itself.
        return runCatching { PdfRenderers.use(context, descriptor) { it.pageCount } }
            .onFailure { runCatching { descriptor.close() } }
            .getOrNull()
    }
}
