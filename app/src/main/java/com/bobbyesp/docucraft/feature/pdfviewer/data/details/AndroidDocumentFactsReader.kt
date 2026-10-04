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
import com.bobbyesp.docucraft.feature.pdfviewer.domain.details.pdfVersionIn
import com.bobbyesp.scanner.ContentRef
import com.composepdf.PdfRenderers
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Size from the provider (or the file system), page count from the platform renderer, and the
 * version of PDF from the file's first bytes. Any can fail on a file another app handed over, and
 * no such failure is worth more than a missing row in the details, so each comes back `null` rather
 * than throwing.
 */
class AndroidDocumentFactsReader(private val context: Context) : DocumentFactsReader {

    override suspend fun read(document: ContentRef): DocumentFacts =
        withContext(Dispatchers.IO) {
            val uri = document.value.toUri()
            DocumentFacts(
                sizeBytes = runCatching { size(uri) }.getOrNull(),
                pageCount = pages(uri),
                pdfVersion = version(uri),
            )
        }

    override suspend fun pdfVersion(document: ContentRef): String? =
        withContext(Dispatchers.IO) { version(document.value.toUri()) }

    private fun version(uri: android.net.Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val header = ByteArray(HeaderLength)
            val read = input.read(header)
            if (read <= 0) null
            // Latin-1, so that whatever bytes come before the header stay one char each.
            else pdfVersionIn(String(header, 0, read, Charsets.ISO_8859_1))
        }
    }
        .getOrNull()

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

    private companion object {
        /** How far into a file its header is looked for: readers accept it within the first KB. */
        const val HeaderLength = 1024
    }
}
