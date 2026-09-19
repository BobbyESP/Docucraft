/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.mapper

import androidx.core.net.toUri
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.ScannedDocumentEntity
import com.bobbyesp.docucraft.feature.docscanner.domain.model.NewScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument

/**
 * Translates between the Room rows and the domain models.
 *
 * Lives here, rather than on the models, so that the domain never has to know a database is what
 * happens to be behind the catalogue.
 */
internal fun ScannedDocumentEntity.toModel(): ScannedDocument =
    ScannedDocument(
        id = id,
        uuid = uuid,
        filename = filename,
        title = title,
        description = description,
        path = path.toUri(),
        createdTimestamp = createdTimestamp,
        fileSize = fileSize,
        pageCount = pageCount,
        thumbnail = thumbnail,
    )

/** A document that has never been catalogued, so it carries no id and no user-supplied fields. */
internal fun NewScannedDocument.toEntity(): ScannedDocumentEntity =
    ScannedDocumentEntity(
        filename = filename,
        title = null,
        description = null,
        path = location.value,
        createdTimestamp = createdTimestamp,
        fileSize = fileSizeBytes,
        pageCount = pageCount,
        thumbnail = thumbnail?.value,
    )
