/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.core.util.DateTime
import com.bobbyesp.docucraft.feature.docscanner.domain.exception.ScanSaveException
import com.bobbyesp.docucraft.feature.docscanner.domain.model.NewScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.LocalDocumentsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.scanner.ContentRef
import com.bobbyesp.docucraft.feature.docscanner.domain.scanner.ScanDraft
import com.bobbyesp.docucraft.feature.docscanner.domain.storage.DocumentStorage

/**
 * Turns a finished scan into a document the app owns and knows about.
 *
 * Only the order of operations lives here: store the file, confirm it is real, try for a preview,
 * then catalogue it. Where the file goes and how the preview is rendered are [DocumentStorage]'s
 * problem, and nothing in this class names a framework, a file system or a database.
 */
class SaveScanDraftUseCase(
    private val storage: DocumentStorage,
    private val repository: LocalDocumentsRepository,
) {
    /**
     * @param filename Name without extension. Defaults to one derived from the capture time.
     * @return Where the saved document lives.
     */
    suspend operator fun invoke(draft: ScanDraft, filename: String? = null): Result<ContentRef> =
        runCatching {
            val pdf = draft.pdf ?: throw ScanSaveException.NothingToSave()
            val name = filename ?: defaultFilename(draft.capturedAtEpochMillis)

            val stored = storage.storeDocument(source = pdf.content, filename = name)
            if (stored.sizeBytes <= 0) throw ScanSaveException.OutputFileEmpty()

            // A document without a preview is still a document, so this must not fail the save.
            val thumbnail =
                runCatching { storage.storeThumbnail(stored.location, name) }.getOrNull()

            repository.saveDocument(
                NewScannedDocument(
                    filename = name,
                    location = stored.location,
                    createdTimestamp = draft.capturedAtEpochMillis,
                    fileSizeBytes = stored.sizeBytes,
                    pageCount = pdf.pageCount,
                    thumbnail = thumbnail,
                )
            )

            stored.location
        }

    private fun defaultFilename(capturedAtEpochMillis: Long): String {
        val stamp =
            DateTime.formatDateTime(
                timestampMillis = capturedAtEpochMillis,
                pattern = "yyyyMMdd_HHmmss",
            )
        return "Scan_$stamp"
    }
}
