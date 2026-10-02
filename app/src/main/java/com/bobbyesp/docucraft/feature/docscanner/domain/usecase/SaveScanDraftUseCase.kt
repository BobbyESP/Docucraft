/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.core.util.DateTime
import com.bobbyesp.docucraft.feature.docscanner.domain.exception.ScanSaveException
import com.bobbyesp.docucraft.feature.docscanner.domain.model.NewScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.DocumentsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.storage.DocumentStorage
import com.bobbyesp.scanner.ContentRef
import com.bobbyesp.scanner.ScanDraft

/**
 * Turns a finished scan into a document the app owns and knows about.
 *
 * Only the order of operations lives here: store the file, confirm it is real, then catalogue it.
 * Where the file goes is [DocumentStorage]'s problem, and nothing in this class names a framework,
 * a file system or a database.
 */
class SaveScanDraftUseCase(
    private val storage: DocumentStorage,
    private val repository: DocumentsRepository,
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

            repository.saveDocument(
                NewScannedDocument(
                    filename = name,
                    location = stored.location,
                    capturedAtEpochMillis = draft.capturedAtEpochMillis,
                    sizeBytes = stored.sizeBytes,
                    pageCount = pdf.pageCount,
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
