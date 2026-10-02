/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.DocumentEntity
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.DocumentTagEntity
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Query("SELECT * FROM tags ORDER BY normalized_name") fun observeAll(): Flow<List<TagEntity>>

    @Query(
        "SELECT t.* FROM tags t JOIN document_tags dt ON dt.tag_id = t.id " +
            "JOIN documents d ON d.id = dt.document_id " +
            "WHERE d.uuid = :documentUuid ORDER BY t.normalized_name"
    )
    fun observeOf(documentUuid: String): Flow<List<TagEntity>>

    /**
     * The library's documents that carry every one of the tags: a document matches as many times as
     * it has of them, and has them all when that is [tagCount].
     */
    @Query(
        "SELECT d.* FROM library_documents d " +
            "JOIN document_tags dt ON dt.document_id = d.id " +
            "JOIN tags t ON t.id = dt.tag_id " +
            "WHERE t.uuid IN (:tagUuids) GROUP BY d.id HAVING COUNT(*) = :tagCount " +
            "ORDER BY d.created_at DESC"
    )
    fun observeDocumentsWithAll(tagUuids: List<String>, tagCount: Int): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM tags WHERE uuid = :uuid") suspend fun byUuid(uuid: String): TagEntity?

    @Query("SELECT * FROM tags WHERE normalized_name = :normalizedName")
    suspend fun byNormalizedName(normalizedName: String): TagEntity?

    @Insert suspend fun insert(tag: TagEntity): Long

    @Update suspend fun update(tag: TagEntity)

    /** Its place on every document goes with it. */
    @Query("DELETE FROM tags WHERE uuid = :uuid") suspend fun delete(uuid: String)

    /** The row id of a document the app keeps, or `null`: no other document is tagged. */
    @Query("SELECT id FROM documents WHERE uuid = :uuid AND custody = 'MANAGED'")
    suspend fun managedDocumentId(uuid: String): Long?

    /** Putting a tag where it already is changes nothing, not even when it was put there. */
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(link: DocumentTagEntity)

    @Query(
        "DELETE FROM document_tags " +
            "WHERE document_id = (SELECT id FROM documents WHERE uuid = :documentUuid) " +
            "AND tag_id = (SELECT id FROM tags WHERE uuid = :tagUuid)"
    )
    suspend fun untag(documentUuid: String, tagUuid: String)

    @Query("UPDATE tags SET home_position = NULL WHERE home_position IS NOT NULL")
    suspend fun clearHomeSections()

    @Query("UPDATE tags SET home_position = :position WHERE uuid = :uuid")
    suspend fun setHomePosition(uuid: String, position: Int)
}
