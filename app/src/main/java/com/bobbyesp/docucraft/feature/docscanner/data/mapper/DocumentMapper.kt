/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.mapper

import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.DocumentCustody
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.DocumentEntity
import com.bobbyesp.docucraft.feature.docscanner.data.storage.DocumentLocations
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.model.DocumentOrigin
import com.bobbyesp.docucraft.feature.docscanner.domain.model.NewScan
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.NewLinkedDocument
import com.bobbyesp.scanner.ContentRef

/**
 * Where the storage vocabulary meets the domain one.
 *
 * The table holds both kinds of document and tells them apart by a column; the domain has a type
 * for each, so a managed document cannot be asked for a URI it does not have. The row id stays
 * behind: nothing above this layer uses it.
 *
 * The catalogue keeps a managed document's path relative to the files directory; the location to
 * open it from is worked out here through [locations].
 */
internal fun DocumentEntity.toModel(locations: DocumentLocations): Document =
    when (custody) {
        DocumentCustody.MANAGED -> toManaged(locations)
        DocumentCustody.LINKED -> toLinked()
    }

/** For a row known to be of a document the app keeps, such as any row of the library. */
internal fun DocumentEntity.toManaged(locations: DocumentLocations): Document.Managed {
    val filePath = checkNotNull(filePath) { "Managed document $uuid has no file" }
    return Document.Managed(
        uuid = uuid,
        originalName = originalName,
        title = title,
        suggestedTitle = suggestedTitle,
        description = description,
        location = locations.locationOf(filePath),
        sizeBytes = sizeBytes,
        pageCount = pageCount,
        createdAtEpochMillis = createdAt,
        filePath = filePath,
        origin = checkNotNull(origin) { "Managed document $uuid has no origin" },
        capturedAtEpochMillis = capturedAt,
        contentUpdatedAtEpochMillis = contentUpdatedAt,
        isFavorite = isFavorite,
        ocrEnabled = ocrEnabled,
        trashedAtEpochMillis = trashedAt,
    )
}

private fun DocumentEntity.toLinked(): Document.Linked =
    Document.Linked(
        uuid = uuid,
        originalName = originalName,
        title = title,
        suggestedTitle = suggestedTitle,
        description = description,
        location = ContentRef(checkNotNull(uri) { "Linked document $uuid has no URI" }),
        sizeBytes = sizeBytes,
        pageCount = pageCount,
        createdAtEpochMillis = createdAt,
        hasPersistedPermission = hasPersistedPermission == true,
    )

/**
 * A document of another app the catalogue is about to refer to. Nothing is known of it yet but
 * where it is and what it is called there.
 */
internal fun NewLinkedDocument.toEntity(uuid: String, createdAt: Long): DocumentEntity =
    DocumentEntity(
        uuid = uuid,
        custody = DocumentCustody.LINKED,
        origin = null,
        originalName = originalName,
        title = null,
        suggestedTitle = null,
        description = null,
        mimeType = DocumentEntity.MIME_TYPE_PDF,
        sizeBytes = null,
        pageCount = null,
        contentHash = null,
        isEncrypted = false,
        pdfAuthor = null,
        pdfSubject = null,
        pdfKeywords = null,
        pdfCreatedAt = null,
        documentDate = null,
        folderId = null,
        isFavorite = false,
        ocrEnabled = false,
        filePath = null,
        uri = location.value,
        hasPersistedPermission = hasPersistedPermission,
        sourceUri = null,
        sourceModifiedAt = null,
        capturedAt = null,
        createdAt = createdAt,
        updatedAt = createdAt,
        contentUpdatedAt = createdAt,
        trashedAt = null,
    )

/**
 * A document that has never been catalogued: a scan the app now keeps, with no user-supplied fields
 * yet.
 *
 * @param createdAt When it enters the catalogue, which is not when it was captured.
 */
internal fun NewScan.toEntity(createdAt: Long): DocumentEntity =
    DocumentEntity(
        uuid = uuid,
        custody = DocumentCustody.MANAGED,
        origin = DocumentOrigin.SCAN,
        originalName = originalName,
        title = null,
        suggestedTitle = null,
        description = null,
        mimeType = DocumentEntity.MIME_TYPE_PDF,
        sizeBytes = sizeBytes,
        pageCount = pageCount,
        contentHash = contentHash,
        isEncrypted = false,
        pdfAuthor = null,
        pdfSubject = null,
        pdfKeywords = null,
        pdfCreatedAt = null,
        documentDate = null,
        folderId = null,
        isFavorite = false,
        ocrEnabled = false,
        filePath = filePath,
        uri = null,
        hasPersistedPermission = null,
        sourceUri = null,
        sourceModifiedAt = null,
        capturedAt = capturedAtEpochMillis,
        createdAt = createdAt,
        updatedAt = createdAt,
        contentUpdatedAt = createdAt,
        trashedAt = null,
    )
