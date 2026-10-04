/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.core.net.toUri
import com.bobbyesp.docucraft.feature.docscanner.data.db.dao.DocumentDao
import com.bobbyesp.docucraft.feature.docscanner.data.service.DocumentOperationsService
import com.bobbyesp.docucraft.feature.docscanner.domain.model.DocumentThumbnail
import com.bobbyesp.docucraft.feature.docscanner.domain.storage.DocumentThumbnails
import com.bobbyesp.scanner.ContentRef
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Keeps previews in the cache directory, one file per document and version, drawn from the
 * document's first page the first time each is asked for.
 *
 * The cache directory is the right place for them: the system may empty it when space is short, it
 * is never backed up, and losing a preview costs nothing but drawing it again.
 */
class CachedDocumentThumbnails(
    private val context: Context,
    private val documentDao: DocumentDao,
    private val documentOperations: DocumentOperationsService,
) : DocumentThumbnails {

    private val directory: File
        get() = File(context.cacheDir, DIRECTORY)

    /**
     * One preview is drawn at a time. A list and a shelf showing the same document ask for the same
     * preview at once, and would otherwise both write it.
     */
    private val drawing = Mutex()

    override suspend fun get(thumbnail: DocumentThumbnail): ContentRef? =
        withContext(Dispatchers.IO) {
            val file = fileOf(thumbnail)
            val ready = file.exists() || drawing.withLock { file.exists() || draw(thumbnail, file) }
            if (ready) ContentRef(file.path) else null
        }

    override suspend fun discard(documentUuid: String) {
        withContext(Dispatchers.IO) { previewsOf(documentUuid).forEach { it.delete() } }
    }

    override suspend fun retainOnly(documentUuids: Set<String>) {
        withContext(Dispatchers.IO) {
            directory
                .listFiles()
                .orEmpty()
                .filter { it.name.substringBefore('.') !in documentUuids }
                .forEach { it.delete() }
            // Previews were files of the app once, named after the document's file.
            File(context.filesDir, LEGACY_DIRECTORY).deleteRecursively()
        }
    }

    private suspend fun draw(thumbnail: DocumentThumbnail, into: File): Boolean {
        val filePath = documentDao.filePathOf(thumbnail.documentUuid) ?: return false
        val document = File(context.filesDir, filePath)
        if (!document.exists()) return false

        directory.mkdirs()
        // Written under another name first: a file with the final name is a whole preview.
        val unfinished = File(directory, "${into.name}.tmp")
        val drawn =
            documentOperations.saveDocumentPageAsImage(
                documentUri = document.toUri(),
                outputFile = unfinished,
                pageIndex = 0,
                format = format,
                quality = QUALITY,
            ) && unfinished.renameTo(into)

        if (!drawn) {
            unfinished.delete()
            return false
        }

        // The previews of the content this document had before will not be asked for again.
        previewsOf(thumbnail.documentUuid).filter { it != into }.forEach { it.delete() }
        return true
    }

    private fun fileOf(thumbnail: DocumentThumbnail): File =
        File(directory, "${thumbnail.documentUuid}.${thumbnail.contentVersion}.$EXTENSION")

    private fun previewsOf(documentUuid: String): List<File> =
        directory.listFiles { file -> file.name.startsWith("$documentUuid.") }?.toList().orEmpty()

    private val format: Bitmap.CompressFormat
        get() =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
            }

    private companion object {
        const val DIRECTORY = "thumbnails"
        const val LEGACY_DIRECTORY = "previews"
        const val EXTENSION = "webp"
        const val QUALITY = 65
    }
}
