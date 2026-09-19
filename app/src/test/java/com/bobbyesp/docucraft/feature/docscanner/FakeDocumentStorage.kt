/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner

import com.bobbyesp.docucraft.feature.docscanner.domain.storage.DocumentStorage
import com.bobbyesp.docucraft.feature.docscanner.domain.storage.StoredDocument
import com.bobbyesp.scanner.ContentRef

/**
 * Storage that keeps a ledger instead of files.
 *
 * Shared by everything that exercises the storage port, so the cases that used to need a device —
 * an empty file, a preview that will not render, a delete that fails — are just fields.
 */
class FakeDocumentStorage : DocumentStorage {

    var sizeBytes = 1_024L
    var thumbnail: ContentRef? = ContentRef("/previews/scan.png")
    var storeFailure: Exception? = null
    var thumbnailFailure: Exception? = null
    var deleteFailure: Exception? = null

    var usedFilename: String? = null
        private set

    val deleted = mutableListOf<ContentRef>()

    override suspend fun storeDocument(source: ContentRef, filename: String): StoredDocument {
        storeFailure?.let { throw it }
        usedFilename = filename
        return StoredDocument(ContentRef("content://stored/$filename.pdf"), sizeBytes)
    }

    override suspend fun storeThumbnail(document: ContentRef, filename: String): ContentRef? {
        thumbnailFailure?.let { throw it }
        return thumbnail
    }

    override suspend fun delete(location: ContentRef) {
        deleteFailure?.let { throw it }
        deleted += location
    }
}
