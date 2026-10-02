/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.DocumentActivityEntity
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.DocumentEntity
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.PageEntity
import com.bobbyesp.docucraft.feature.docscanner.domain.model.DocumentAvailability
import com.bobbyesp.docucraft.feature.docscanner.domain.model.PageTextStatus
import kotlinx.coroutines.flow.Flow

@Dao
abstract class DocumentDao {

    /** The library, newest first, emitted again whenever a document changes. */
    @Query("SELECT * FROM library_documents ORDER BY created_at DESC")
    abstract fun observeLibrary(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE uuid = :uuid")
    abstract suspend fun getByUuid(uuid: String): DocumentEntity?

    /** Where the file of a document the app keeps is, relative to the files directory. */
    @Query("SELECT file_path FROM documents WHERE uuid = :uuid")
    abstract suspend fun filePathOf(uuid: String): String?

    /** Emits again whenever the row changes, and emits null once it is gone. */
    @Query("SELECT * FROM documents WHERE uuid = :uuid")
    abstract fun observeByUuid(uuid: String): Flow<DocumentEntity?>

    /**
     * Adds a document the app keeps, together with what cannot exist without it: its activity, and
     * one page for each page of its file, all still to be read. In one transaction, so that a
     * document is never found without them.
     *
     * A conflict aborts instead of replacing. Replacing would delete the document that already has
     * that uuid or that file, and no write removes a document on its own.
     *
     * @return The row id of the new document.
     */
    @Transaction
    open suspend fun insertManaged(document: DocumentEntity): Long {
        val id = insert(document)
        insert(
            DocumentActivityEntity(
                documentId = id,
                lastOpenedAt = null,
                lastActivityAt = document.createdAt,
                readingPage = null,
                readingOffset = null,
                availability = DocumentAvailability.AVAILABLE,
                availabilityCheckedAt = document.createdAt,
            )
        )
        insert(
            List(document.pageCount ?: 0) { index ->
                PageEntity(
                    documentId = id,
                    pageIndex = index,
                    widthPt = null,
                    heightPt = null,
                    textStatus = PageTextStatus.PENDING,
                    textOrigin = null,
                    confidence = null,
                    engine = null,
                    extractorVersion = null,
                    language = null,
                    attempts = 0,
                    extractedAt = null,
                )
            }
        )
        return id
    }

    @Insert protected abstract suspend fun insert(document: DocumentEntity): Long

    @Insert protected abstract suspend fun insert(activity: DocumentActivityEntity)

    @Insert protected abstract suspend fun insert(pages: List<PageEntity>)

    /**
     * Replaces the two fields the user writes.
     *
     * @return How many documents were changed: none when there is no document with this [uuid].
     */
    @Query(
        "UPDATE documents SET title = :title, description = :description, updated_at = :updatedAt " +
            "WHERE uuid = :uuid"
    )
    abstract suspend fun updateFields(
        uuid: String,
        title: String?,
        description: String?,
        updatedAt: Long,
    ): Int

    /** Its activity, pages and tags go with it, and the full-text indexes forget it. */
    @Query("DELETE FROM documents WHERE uuid = :uuid")
    abstract suspend fun deleteByUuid(uuid: String): Int
}
