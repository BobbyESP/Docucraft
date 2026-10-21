/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bobbyesp.docucraft.feature.docscanner.domain.model.DocumentOrigin

/**
 * A document of the catalogue, as Room stores it: one the app keeps, or one that belongs to another
 * app and is only referred to.
 *
 * Both kinds share the table, told apart by [custody]. Recents lists them together, and saving a
 * linked document into the library is then a single update that keeps its [uuid]. Which columns
 * each kind may fill is checked by the `documents_custody_*` triggers, since Room cannot declare a
 * `CHECK`.
 *
 * Nothing here changes when a document is merely opened or read. Every update of this table
 * re-indexes the row in [DocumentFtsEntity] and makes the library's flows emit again, so what
 * changes with use is in [DocumentActivityEntity].
 *
 * @property id Row id, and the `docid` of the full-text index. It never leaves the data layer.
 * @property uuid The document's identity everywhere else. It never changes.
 * @property origin How a managed document got here. `null` in a linked one.
 * @property originalName The name the scanner or the other app gave it, without extension.
 * @property suggestedTitle A title proposed from the PDF's metadata or its first page, shown only
 *   while the user has written none.
 * @property pageCount `null` while it is not known, as in a linked document that was never read.
 * @property contentHash SHA-256 of the file, in hexadecimal. Not unique: importing a duplicate is
 *   allowed.
 * @property documentDate The day the document is about, as an epoch day: no time, no zone.
 * @property folderId `null` in the root of the library.
 * @property filePath Where a managed document's file is, relative to the app's files directory.
 * @property uri Where a linked document is. It is what identifies it between opens.
 * @property sourceUri Where an imported document was copied from.
 * @property contentUpdatedAt When the file's bytes last changed, which is also the version of its
 *   preview.
 * @property trashedAt When it went to the bin, or `null` while it is in the library.
 */
@Entity(
    tableName = "documents",
    foreignKeys =
        [
            ForeignKey(
                entity = FolderEntity::class,
                parentColumns = ["id"],
                childColumns = ["folder_id"],
                // A folder cannot go while documents are in it: they are moved out first.
                onDelete = ForeignKey.RESTRICT,
            )
        ],
    indices =
        [
            Index(value = ["uuid"], unique = true),
            Index(value = ["file_path"], unique = true),
            Index(value = ["uri"], unique = true),
            Index(value = ["folder_id"]),
            Index(value = ["content_hash"]),
            Index(value = ["trashed_at"]),
        ],
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "uuid") val uuid: String,
    @ColumnInfo(name = "custody") val custody: DocumentCustody,
    @ColumnInfo(name = "origin") val origin: DocumentOrigin?,
    @ColumnInfo(name = "original_name") val originalName: String,
    @ColumnInfo(name = "title") val title: String?,
    @ColumnInfo(name = "suggested_title") val suggestedTitle: String?,
    @ColumnInfo(name = "description") val description: String?,
    @ColumnInfo(name = "mime_type", defaultValue = MIME_TYPE_PDF) val mimeType: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long?,
    @ColumnInfo(name = "page_count") val pageCount: Int?,
    @ColumnInfo(name = "content_hash") val contentHash: String?,
    @ColumnInfo(name = "is_encrypted") val isEncrypted: Boolean,
    @ColumnInfo(name = "pdf_author") val pdfAuthor: String?,
    @ColumnInfo(name = "pdf_subject") val pdfSubject: String?,
    @ColumnInfo(name = "pdf_keywords") val pdfKeywords: String?,
    @ColumnInfo(name = "pdf_created_at") val pdfCreatedAt: Long?,
    @ColumnInfo(name = "document_date") val documentDate: Long?,
    @ColumnInfo(name = "folder_id") val folderId: Long?,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean,
    @ColumnInfo(name = "ocr_enabled") val ocrEnabled: Boolean,
    @ColumnInfo(name = "file_path") val filePath: String?,
    @ColumnInfo(name = "uri") val uri: String?,
    @ColumnInfo(name = "has_persisted_permission") val hasPersistedPermission: Boolean?,
    @ColumnInfo(name = "source_uri") val sourceUri: String?,
    @ColumnInfo(name = "source_modified_at") val sourceModifiedAt: Long?,
    @ColumnInfo(name = "captured_at") val capturedAt: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "content_updated_at") val contentUpdatedAt: Long,
    @ColumnInfo(name = "trashed_at") val trashedAt: Long?,
) {
    companion object {
        const val MIME_TYPE_PDF = "application/pdf"
    }
}
