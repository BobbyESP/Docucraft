/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.DocumentEntity
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

/** A folder with the uuid of its parent, which is how the rest of the app refers to it. */
private const val FOLDER_ROWS =
    "SELECT f.*, p.uuid AS parent_uuid FROM folders f LEFT JOIN folders p ON p.id = f.parent_id"

class FolderRow(
    @Embedded val folder: FolderEntity,
    @ColumnInfo(name = "parent_uuid") val parentUuid: String?,
)

/**
 * `parent_id IS :parentId` rather than `=` throughout: the root is a `NULL` parent, and `IS` is the
 * comparison that is true for two `NULL`s.
 */
@Dao
interface FolderDao {

    @Query("$FOLDER_ROWS WHERE f.parent_id IS NULL ORDER BY f.normalized_name")
    fun observeRoot(): Flow<List<FolderRow>>

    @Query("$FOLDER_ROWS WHERE p.uuid = :parentUuid ORDER BY f.normalized_name")
    fun observeChildren(parentUuid: String): Flow<List<FolderRow>>

    @Query("$FOLDER_ROWS WHERE f.pinned_at IS NOT NULL ORDER BY f.pinned_at")
    fun observePinned(): Flow<List<FolderRow>>

    @Query("$FOLDER_ROWS WHERE f.uuid = :uuid") suspend fun rowByUuid(uuid: String): FolderRow?

    @Query("$FOLDER_ROWS WHERE f.id = :id") suspend fun rowById(id: Long): FolderRow?

    @Query("SELECT * FROM folders WHERE uuid = :uuid")
    suspend fun byUuid(uuid: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE parent_id IS :parentId")
    suspend fun childrenOf(parentId: Long?): List<FolderEntity>

    /** Whether a folder other than [exceptId] has that name under that parent. */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM folders WHERE parent_id IS :parentId " +
            "AND normalized_name = :normalizedName AND id != :exceptId)"
    )
    suspend fun isNameTaken(parentId: Long?, normalizedName: String, exceptId: Long): Boolean

    @Insert suspend fun insert(folder: FolderEntity): Long

    @Update suspend fun update(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :id") suspend fun delete(id: Long)

    @Query("SELECT * FROM library_documents WHERE folder_id IS NULL ORDER BY created_at DESC")
    fun observeDocumentsInRoot(): Flow<List<DocumentEntity>>

    @Query(
        "SELECT d.* FROM library_documents d JOIN folders f ON f.id = d.folder_id " +
            "WHERE f.uuid = :folderUuid ORDER BY d.created_at DESC"
    )
    fun observeDocumentsIn(folderUuid: String): Flow<List<DocumentEntity>>

    /** Every document of a folder, the bin's too: none may be left pointing at a deleted folder. */
    @Query("UPDATE documents SET folder_id = :to WHERE folder_id = :from")
    suspend fun moveDocumentsOut(from: Long, to: Long?)

    /** Only documents the app keeps: another app's document is in no folder. */
    @Query(
        "UPDATE documents SET folder_id = :folderId, updated_at = :updatedAt " +
            "WHERE uuid IN (:documentUuids) AND custody = 'MANAGED'"
    )
    suspend fun moveDocuments(documentUuids: List<String>, folderId: Long?, updatedAt: Long)
}
