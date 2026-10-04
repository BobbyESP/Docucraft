/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bobbyesp.docucraft.feature.docscanner.domain.SortOption

/**
 * A folder of the library. Folders nest without a limit here; how deep the app lets the user go is
 * a decision of the interface.
 *
 * @property normalizedName [name] trimmed, lowercased and without diacritics. It is stored because
 *   it is what keeps two folders with the same parent from sharing a name. The index cannot do that
 *   in the root, where [parentId] is `null` and SQLite treats every `NULL` as different: there the
 *   `folders_rules_*` triggers do.
 * @property parentId The folder this one is in, or `null` in the root.
 * @property color A key of the app's palette, never a colour value, so it follows the theme.
 * @property icon A key of the app's icon set, never a resource id, so it survives a rebuild.
 * @property pinnedAt When it was pinned to Home, which is also the order of the pinned folders.
 * @property sortCriteria How its contents are ordered, if the user chose.
 */
@Entity(
    tableName = "folders",
    foreignKeys =
        [
            ForeignKey(
                entity = FolderEntity::class,
                parentColumns = ["id"],
                childColumns = ["parent_id"],
                // A folder cannot go while it has subfolders: they are moved out first.
                onDelete = ForeignKey.RESTRICT,
            )
        ],
    indices =
        [
            Index(value = ["uuid"], unique = true),
            Index(value = ["parent_id", "normalized_name"], unique = true),
        ],
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "uuid") val uuid: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "normalized_name") val normalizedName: String,
    @ColumnInfo(name = "parent_id") val parentId: Long?,
    @ColumnInfo(name = "color") val color: String?,
    @ColumnInfo(name = "icon") val icon: String?,
    @ColumnInfo(name = "pinned_at") val pinnedAt: Long?,
    @ColumnInfo(name = "sort_criteria") val sortCriteria: SortOption.Criteria?,
    @ColumnInfo(name = "sort_order") val sortOrder: SortOption.Order?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
