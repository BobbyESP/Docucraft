/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.core.util.DateTime
import com.bobbyesp.docucraft.feature.docscanner.domain.exception.ScanSaveException
import com.bobbyesp.docucraft.feature.docscanner.domain.model.NewScan
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.DocumentsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.storage.DocumentStorage
import com.bobbyesp.scanner.ScanDraft
import java.util.UUID

/**
 * Turns a finished scan into a document the app owns and knows about.
 *
 * Only the order of operations lives here, and the order is the rule: **the file first, the
 * catalogue after**. A document enters the catalogue only once its file is whole, so the user is
 * never shown a document that cannot be opened. If cataloguing then fails, the file is taken back
 * out: a file nothing points at is invisible, but it is still the user's storage.
 *
 * Where the file goes is [DocumentStorage]'s problem, and nothing in this class names a framework,
 * a file system or a database.
 *
 * @param newUuid What gives the document its identity. A parameter so that a test can know it.
 */
class SaveScanDraftUseCase(
    private val storage: DocumentStorage,
    private val repository: DocumentsRepository,
    private val newUuid: () -> String = { UUID.randomUUID().toString() },
) {
    /** @return The uuid of the saved document. */
    suspend operator fun invoke(draft: ScanDraft): Result<String> = runCatching {
        val pdf = draft.pdf ?: throw ScanSaveException.NothingToSave()
        val uuid = newUuid()

        val stored = storage.storeDocument(source = pdf.content, documentUuid = uuid)

        try {
            repository.addScan(
                NewScan(
                    uuid = uuid,
                    originalName = defaultName(draft.capturedAtEpochMillis),
                    filePath = stored.filePath,
                    sizeBytes = stored.sizeBytes,
                    contentHash = stored.contentHash,
                    // What the scanner says it captured. A scanner that does not say reports
                    // none, and then the pages are those counted in the file itself.
                    pageCount =
                        pdf.pageCount.takeIf { it > 0 }
                            ?: stored.pageCount
                            ?: throw ScanSaveException.UnreadableDocument(),
                    capturedAtEpochMillis = draft.capturedAtEpochMillis,
                )
            )
        } catch (e: Exception) {
            runCatching { storage.delete(stored.filePath) }
            throw e
        }

        uuid
    }

    /** What a scan is called until the user gives it a title. */
    private fun defaultName(capturedAtEpochMillis: Long): String {
        val stamp =
            DateTime.formatDateTime(
                timestampMillis = capturedAtEpochMillis,
                pattern = "yyyyMMdd_HHmmss",
            )
        return "Scan_$stamp"
    }
}
