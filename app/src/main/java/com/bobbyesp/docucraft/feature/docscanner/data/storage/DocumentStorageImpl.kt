/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.storage

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.bobbyesp.docucraft.App
import com.bobbyesp.docucraft.core.util.ensure
import com.bobbyesp.docucraft.core.util.ensureParent
import com.bobbyesp.docucraft.feature.docscanner.data.service.DocumentOperationsService
import com.bobbyesp.docucraft.feature.docscanner.domain.exception.ScanSaveException
import com.bobbyesp.docucraft.feature.docscanner.domain.storage.DocumentStorage
import com.bobbyesp.docucraft.feature.docscanner.domain.storage.StoredDocument
import com.bobbyesp.scanner.ContentRef
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.sink
import io.github.vinceglb.filekit.size
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.buffered

/**
 * Keeps documents in the app's private files directory and hands them out as `FileProvider` URIs,
 * which is what makes them shareable with the viewer and with other apps.
 */
class DocumentStorageImpl(
    private val context: Context,
    private val documentOperations: DocumentOperationsService,
) : DocumentStorage {

    override suspend fun storeDocument(source: ContentRef, filename: String): StoredDocument =
        withContext(Dispatchers.IO) {
            val directory =
                PlatformFile(FileKit.filesDir, DOCUMENTS_DIR).apply { ensure(mustCreate = true) }
            val target = PlatformFile(directory, "$filename.pdf")

            copy(from = source.value.toUri(), to = target)

            val shareableUri =
                FileProvider.getUriForFile(
                    context,
                    App.getAuthority(context),
                    File(target.path),
                )

            StoredDocument(
                location = ContentRef(shareableUri.toString()),
                sizeBytes = target.size(),
            )
        }

    override suspend fun storeThumbnail(document: ContentRef, filename: String): ContentRef? =
        withContext(Dispatchers.IO) {
            val directory =
                PlatformFile(FileKit.filesDir, THUMBNAILS_DIR).apply { ensure(mustCreate = true) }
            val target = PlatformFile(directory, "$filename.png")

            documentOperations.saveDocumentPageAsImage(
                documentUri = document.value.toUri(),
                outputFile = File(target.path),
                pageIndex = 0,
                quality = THUMBNAIL_QUALITY,
            )

            ContentRef(target.path)
        }

    /**
     * Note the `contentResolver.delete`. These are `FileProvider` URIs, and a `FileProvider`
     * exposes no `_data` column, so resolving one back to a path returns null — which is why the
     * previous attempt at deleting by path silently left every document on disk. Asking the
     * provider to delete works, because deleting the underlying file is exactly what it does.
     */
    override suspend fun delete(location: ContentRef) {
        withContext(Dispatchers.IO) {
            val uri = location.value.toUri()

            try {
                when (uri.scheme) {
                    ContentResolver.SCHEME_CONTENT ->
                        context.contentResolver.delete(uri, null, null)
                    // Thumbnails are kept as plain paths, which parse with no scheme at all.
                    ContentResolver.SCHEME_FILE,
                    null -> uri.path?.let { File(it).delete() }
                    else -> Log.w(TAG, "Not deleting, unsupported scheme: ${uri.scheme}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Could not delete ${location.value}", e)
            }
        }
    }

    private fun copy(from: Uri, to: PlatformFile) {
        to.ensureParent(mustCreate = true)

        try {
            to.sink(append = false).buffered().use { sink ->
                val input =
                    context.contentResolver.openInputStream(from)
                        ?: throw IllegalStateException("Could not open input stream for URI: $from")

                input.use { stream ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var read: Int
                    while (stream.read(buffer).also { read = it } != -1) {
                        sink.write(buffer, 0, read)
                    }
                }
            }
        } catch (e: Exception) {
            throw ScanSaveException.OutputFileNotCopied().initCause(e)
        }
    }

    private companion object {
        const val TAG = "DocumentStorage"
        const val DOCUMENTS_DIR = "scans/pdf"
        const val THUMBNAILS_DIR = "previews"
        const val BUFFER_SIZE = 8192
        const val THUMBNAIL_QUALITY = 65
    }
}
