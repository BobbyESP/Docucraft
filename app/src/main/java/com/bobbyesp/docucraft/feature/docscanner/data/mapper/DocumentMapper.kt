/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.mapper

import com.bobbyesp.docucraft.feature.docscanner.data.db.LegacyDocumentPath
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.DocumentCustody
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.DocumentEntity
import com.bobbyesp.docucraft.feature.docscanner.data.storage.DocumentLocations
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.model.DocumentOrigin
import com.bobbyesp.docucraft.feature.docscanner.domain.model.NewScannedDocument
import com.bobbyesp.scanner.ContentRef

/**
 * Where the storage vocabulary meets the domain one.
 *
 * The catalogue keeps a relative path; the domain's document carries a location to open, which is
 * worked out here through [locations]. The row id stays behind: nothing above this layer uses it.
 */
internal fun DocumentEntity.toModel(locations: DocumentLocations): Document =
    Document(
        uuid = uuid,
        filename = originalName,
        title = title,
        description = description,
        location =
            filePath?.let(locations::locationOf)
                ?: ContentRef(checkNotNull(uri) { "Document $uuid has neither a file nor a URI" }),
        capturedAtEpochMillis = capturedAt ?: createdAt,
        // Unknown is 0 in the domain's document, which is what its readers already take it for.
        sizeBytes = sizeBytes ?: 0,
        pageCount = pageCount ?: 0,
        contentUpdatedAtEpochMillis = contentUpdatedAt,
    )

/**
 * A document that has never been catalogued: a scan the app now keeps, with no user-supplied fields
 * yet.
 *
 * @param createdAt When it enters the catalogue, which is not when it was captured.
 */
internal fun NewScannedDocument.toEntity(uuid: String, createdAt: Long): DocumentEntity =
    DocumentEntity(
        uuid = uuid,
        custody = DocumentCustody.MANAGED,
        origin = DocumentOrigin.SCAN,
        originalName = filename,
        title = null,
        suggestedTitle = null,
        description = null,
        mimeType = DocumentEntity.MIME_TYPE_PDF,
        sizeBytes = sizeBytes,
        // A scanner that does not report its pages reports none: unknown, not zero.
        pageCount = pageCount.takeIf { it > 0 },
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
        filePath = LegacyDocumentPath.relativePathOf(location.value, filename),
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
