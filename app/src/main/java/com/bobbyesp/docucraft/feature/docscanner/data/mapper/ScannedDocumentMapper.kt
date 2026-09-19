/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.mapper

import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.ScannedDocumentEntity
import com.bobbyesp.docucraft.feature.docscanner.domain.model.NewScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.scanner.ContentRef

/**
 * Where the storage vocabulary meets the domain one.
 *
 * Column names are the schema and cannot move without a migration, so the renaming happens here,
 * which is what a mapper is for. The row id stays behind: nothing above this layer uses it.
 */
internal fun ScannedDocumentEntity.toModel(): ScannedDocument =
    ScannedDocument(
        uuid = uuid,
        filename = filename,
        title = title,
        description = description,
        location = ContentRef(path),
        capturedAtEpochMillis = createdTimestamp,
        sizeBytes = fileSize,
        pageCount = pageCount,
        thumbnail = thumbnail?.let(::ContentRef),
    )

/** A document that has never been catalogued, so it carries no id and no user-supplied fields. */
internal fun NewScannedDocument.toEntity(): ScannedDocumentEntity =
    ScannedDocumentEntity(
        filename = filename,
        title = null,
        description = null,
        path = location.value,
        createdTimestamp = capturedAtEpochMillis,
        fileSize = sizeBytes,
        pageCount = pageCount,
        thumbnail = thumbnail?.value,
    )
