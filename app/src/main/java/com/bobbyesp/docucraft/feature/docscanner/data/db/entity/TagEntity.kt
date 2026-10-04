/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A tag.
 *
 * @property normalizedName [name] trimmed, lowercased and without diacritics. Unique, so that
 *   "Invoices" and "invoices " are one tag.
 * @property color A key of the app's palette, never a colour value, so it follows the theme.
 * @property homePosition The place of this tag's section in Home, or `null` when it has none.
 */
@Entity(
    tableName = "tags",
    indices =
        [Index(value = ["uuid"], unique = true), Index(value = ["normalized_name"], unique = true)],
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "uuid") val uuid: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "normalized_name") val normalizedName: String,
    @ColumnInfo(name = "color") val color: String?,
    @ColumnInfo(name = "home_position") val homePosition: Int?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/**
 * A tag on a document. Only documents the app keeps are tagged, which the
 * `document_tags_managed_only` trigger checks. It goes when either side does.
 */
@Entity(
    tableName = "document_tags",
    primaryKeys = ["document_id", "tag_id"],
    foreignKeys =
        [
            ForeignKey(
                entity = DocumentEntity::class,
                parentColumns = ["id"],
                childColumns = ["document_id"],
                onDelete = ForeignKey.CASCADE,
            ),
            ForeignKey(
                entity = TagEntity::class,
                parentColumns = ["id"],
                childColumns = ["tag_id"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
    indices = [Index(value = ["tag_id"])],
)
data class DocumentTagEntity(
    @ColumnInfo(name = "document_id") val documentId: Long,
    @ColumnInfo(name = "tag_id") val tagId: Long,
    @ColumnInfo(name = "tagged_at") val taggedAt: Long,
)
