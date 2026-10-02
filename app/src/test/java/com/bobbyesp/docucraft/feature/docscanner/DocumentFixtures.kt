/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner

import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.model.DocumentThumbnail
import com.bobbyesp.docucraft.feature.docscanner.domain.storage.DocumentThumbnails
import com.bobbyesp.scanner.ContentRef

/**
 * A document for a test that only cares about some of what a document has. Shared, so that a change
 * to the model is made here once and not in every test that builds one.
 */
fun testDocument(
    uuid: String = "doc-1",
    filename: String = "Scan_$uuid",
    title: String? = null,
    description: String? = null,
    location: ContentRef = ContentRef("content://stored/$uuid.pdf"),
    capturedAtEpochMillis: Long = 1_000L,
    sizeBytes: Long = 2_048L,
    pageCount: Int = 1,
    contentUpdatedAtEpochMillis: Long = capturedAtEpochMillis,
) =
    Document(
        uuid = uuid,
        filename = filename,
        title = title,
        description = description,
        location = location,
        capturedAtEpochMillis = capturedAtEpochMillis,
        sizeBytes = sizeBytes,
        pageCount = pageCount,
        contentUpdatedAtEpochMillis = contentUpdatedAtEpochMillis,
    )

/** A cache of previews that only remembers what it was told to forget. */
class FakeDocumentThumbnails : DocumentThumbnails {

    val discarded = mutableListOf<String>()

    override suspend fun get(thumbnail: DocumentThumbnail): ContentRef? = null

    override suspend fun discard(documentUuid: String) {
        discarded += documentUuid
    }
}
