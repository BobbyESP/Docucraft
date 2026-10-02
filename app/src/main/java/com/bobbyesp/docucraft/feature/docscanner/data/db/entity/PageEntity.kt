/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bobbyesp.docucraft.feature.docscanner.domain.model.PageTextStatus
import com.bobbyesp.documentcontent.ContentOrigin

/**
 * A page of a document the app keeps, and how far reading its text has got. Pages are created with
 * their document and go with it.
 *
 * A page is identified by its document and its [pageIndex]. It has a row id of its own as well,
 * because its text hangs from it and a full-text index needs a stable integer to point at.
 *
 * The text itself is in [PageTextEntity], so that a change of [textStatus] does not re-index it.
 *
 * @property pageIndex Zero-based.
 * @property widthPt Width in PDF points (1/72 inch), once the page has been read.
 * @property textOrigin Where the text came from. `null` unless [textStatus] is `EXTRACTED`.
 * @property confidence How sure text recognition was, from 0 to 1. Recognized text only.
 * @property engine What read the text, such as `platform` or `mlkit-latin`.
 * @property extractorVersion The version of the extractor that read it. Pages read by an older one
 *   are read again when it improves.
 * @property language BCP 47 tag of the text, when it could be told.
 * @property attempts How many times reading it has failed.
 */
@Entity(
    tableName = "pages",
    foreignKeys =
        [
            ForeignKey(
                entity = DocumentEntity::class,
                parentColumns = ["id"],
                childColumns = ["document_id"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices =
        [
            Index(value = ["document_id", "page_index"], unique = true),
            Index(value = ["text_status"]),
        ],
)
data class PageEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "document_id") val documentId: Long,
    @ColumnInfo(name = "page_index") val pageIndex: Int,
    @ColumnInfo(name = "width_pt") val widthPt: Float?,
    @ColumnInfo(name = "height_pt") val heightPt: Float?,
    @ColumnInfo(name = "text_status") val textStatus: PageTextStatus,
    @ColumnInfo(name = "text_origin") val textOrigin: ContentOrigin?,
    @ColumnInfo(name = "confidence") val confidence: Float?,
    @ColumnInfo(name = "engine") val engine: String?,
    @ColumnInfo(name = "extractor_version") val extractorVersion: Int?,
    @ColumnInfo(name = "language") val language: String?,
    @ColumnInfo(name = "attempts") val attempts: Int,
    @ColumnInfo(name = "extracted_at") val extractedAt: Long?,
)

/**
 * The text of a page, in reading order. A table of its own because it is the content of the
 * full-text index [PageTextFtsEntity]: [pageId] is the index's `docid`.
 */
@Entity(
    tableName = "page_texts",
    foreignKeys =
        [
            ForeignKey(
                entity = PageEntity::class,
                parentColumns = ["id"],
                childColumns = ["page_id"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
)
data class PageTextEntity(
    @PrimaryKey @ColumnInfo(name = "page_id") val pageId: Long,
    @ColumnInfo(name = "text") val text: String,
)

/**
 * Where each word of a recognized page is, so that its text can be selected without recognizing it
 * again. One blob per page: it is only ever loaded whole, and a row per word would be hundreds of
 * thousands of rows nothing queries.
 *
 * Not a data class: two layouts are not compared, and an array would make the comparison wrong.
 *
 * @property formatVersion The version of the binary format of [data].
 */
@Entity(
    tableName = "page_layouts",
    foreignKeys =
        [
            ForeignKey(
                entity = PageEntity::class,
                parentColumns = ["id"],
                childColumns = ["page_id"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
)
class PageLayoutEntity(
    @PrimaryKey @ColumnInfo(name = "page_id") val pageId: Long,
    @ColumnInfo(name = "format_version") val formatVersion: Int,
    @ColumnInfo(name = "data") val data: ByteArray,
)
