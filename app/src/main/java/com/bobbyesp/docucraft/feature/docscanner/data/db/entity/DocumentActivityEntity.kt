/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bobbyesp.docucraft.feature.docscanner.domain.model.DocumentAvailability

/**
 * What changes about a document as it is used: when it was opened, where the reader left it, and
 * whether its file could be reached. One row per document, created with it.
 *
 * It is a table of its own because it is written on every open and every page turn. In `documents`
 * each of those writes would re-index the document's text and make every list of the library emit.
 *
 * @property lastOpenedAt `null` until the document is opened for the first time.
 * @property lastActivityAt The later of when the document entered the catalogue and when it was
 *   last opened. It is stored, although it can be derived, because Recents is ordered by it and
 *   needs the index. Whoever writes [lastOpenedAt] writes this too.
 * @property readingPage Zero-based page the reader left the document on, if that is remembered.
 * @property readingOffset How far along that page, from 0 to 1.
 */
@Entity(
    tableName = "document_activity",
    foreignKeys =
        [
            ForeignKey(
                entity = DocumentEntity::class,
                parentColumns = ["id"],
                childColumns = ["document_id"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index(value = ["last_activity_at"])],
)
data class DocumentActivityEntity(
    @PrimaryKey @ColumnInfo(name = "document_id") val documentId: Long,
    @ColumnInfo(name = "last_opened_at") val lastOpenedAt: Long?,
    @ColumnInfo(name = "last_activity_at") val lastActivityAt: Long,
    @ColumnInfo(name = "reading_page") val readingPage: Int?,
    @ColumnInfo(name = "reading_offset") val readingOffset: Float?,
    @ColumnInfo(name = "availability") val availability: DocumentAvailability,
    @ColumnInfo(name = "availability_checked_at") val availabilityCheckedAt: Long?,
)
