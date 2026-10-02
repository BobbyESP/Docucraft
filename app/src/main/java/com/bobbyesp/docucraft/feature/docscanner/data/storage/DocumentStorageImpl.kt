/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.bobbyesp.docucraft.feature.docscanner.data.service.DocumentOperationsService
import com.bobbyesp.docucraft.feature.docscanner.domain.exception.ScanSaveException
import com.bobbyesp.docucraft.feature.docscanner.domain.storage.DocumentStorage
import com.bobbyesp.docucraft.feature.docscanner.domain.storage.StoredDocument
import com.bobbyesp.scanner.ContentRef
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Keeps documents in the app's private files directory, each named after its document's uuid:
 * `documents/<uuid>.pdf`.
 *
 * Documents saved before files were named this way are in `scans/pdf/`, under the name their scan
 * had. They stay there: the catalogue says where each document is, so nothing needs them moved.
 */
class DocumentStorageImpl(
    private val context: Context,
    private val documentOperations: DocumentOperationsService,
) : DocumentStorage {

    override suspend fun storeDocument(source: ContentRef, documentUuid: String): StoredDocument =
        withContext(Dispatchers.IO) {
            val directory = File(context.filesDir, DIRECTORY)
            val file = File(directory, "$documentUuid.$EXTENSION")
            // Written under another name first. A file with its final name is a whole document,
            // even if the process dies half way through a copy.
            val unfinished = File(directory, "${file.name}.tmp")

            try {
                directory.mkdirs()
                val copied = copy(from = source.value.toUri(), to = unfinished)
                if (copied.sizeBytes == 0L) throw ScanSaveException.OutputFileEmpty()

                val pageCount = documentOperations.pageCount(unfinished)

                if (!unfinished.renameTo(file)) throw ScanSaveException.OutputFileNotCopied()

                StoredDocument(
                    filePath = "$DIRECTORY/${file.name}",
                    sizeBytes = copied.sizeBytes,
                    contentHash = copied.sha256,
                    pageCount = pageCount,
                )
            } catch (e: Exception) {
                unfinished.delete()
                throw e as? ScanSaveException
                    ?: ScanSaveException.OutputFileNotCopied().initCause(e)
            }
        }

    override suspend fun delete(filePath: String) {
        withContext(Dispatchers.IO) {
            val file = File(context.filesDir, filePath)
            // The path comes from the catalogue. Nothing outside the files directory is this
            // storage's to remove, whatever a path says.
            if (file.canonicalPath.startsWith(context.filesDir.canonicalPath + File.separator)) {
                file.delete()
            } else {
                Log.w(TAG, "Not deleting a file outside the app's storage: $filePath")
            }
        }
    }

    /** Copies while hashing, so the bytes are read once. */
    private fun copy(from: Uri, to: File): Copied {
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L

        val input =
            context.contentResolver.openInputStream(from)
                ?: throw IllegalStateException("Could not open input stream for URI: $from")

        input.use { stream ->
            to.outputStream().use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = stream.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    size += read
                }
            }
        }

        return Copied(size, digest.digest().joinToString("") { "%02x".format(it) })
    }

    private class Copied(val sizeBytes: Long, val sha256: String)

    internal companion object {
        private const val TAG = "DocumentStorage"
        private const val EXTENSION = "pdf"
        private const val BUFFER_SIZE = 8192

        /**
         * The folder documents are saved in. `filepathsprovider.xml` serves it under the same name,
         * which is how a file here is told apart from an older one in a provider URI.
         */
        const val DIRECTORY = "documents"
    }
}
