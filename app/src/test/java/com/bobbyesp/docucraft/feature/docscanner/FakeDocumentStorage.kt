/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner

import com.bobbyesp.docucraft.feature.docscanner.domain.exception.ScanSaveException
import com.bobbyesp.docucraft.feature.docscanner.domain.storage.DocumentStorage
import com.bobbyesp.docucraft.feature.docscanner.domain.storage.StoredDocument
import com.bobbyesp.scanner.ContentRef

/**
 * Storage that keeps a ledger instead of files.
 *
 * Shared by everything that exercises the storage port: the cases a device makes hard to reach — an
 * empty file, a file that is not a document, a copy that fails — are just fields.
 */
class FakeDocumentStorage : DocumentStorage {

    var sizeBytes = 1_024L

    /** What counting the pages of the stored file gives; `null` for a file that cannot be read. */
    var pageCount: Int? = 3
    var storeFailure: Exception? = null
    var deleteFailure: Exception? = null

    /** The paths of the files that are in storage now. */
    val files = mutableListOf<String>()

    val deleted = mutableListOf<String>()

    override suspend fun storeDocument(source: ContentRef, documentUuid: String): StoredDocument {
        storeFailure?.let { throw it }
        // As the port promises: an empty document is not left in storage.
        if (sizeBytes == 0L) throw ScanSaveException.OutputFileEmpty()

        val filePath = "documents/$documentUuid.pdf"
        files += filePath
        return StoredDocument(
            filePath = filePath,
            sizeBytes = sizeBytes,
            contentHash = "hash-of-$documentUuid",
            pageCount = pageCount,
        )
    }

    override suspend fun pageCount(filePath: String): Int? = pageCount.takeIf { filePath in files }

    override suspend fun delete(filePath: String) {
        deleteFailure?.let { throw it }
        files -= filePath
        deleted += filePath
    }
}
