/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.DocumentActivityEntity
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.DocumentEntity
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.PageEntity
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.pendingPages
import com.bobbyesp.docucraft.feature.docscanner.domain.model.DocumentAvailability
import kotlinx.coroutines.flow.Flow

/** A linked document by what identifies it above the data layer and by where it is. */
data class LinkedDocumentRef(
    @ColumnInfo(name = "uuid") val uuid: String,
    @ColumnInfo(name = "uri") val uri: String,
)

/**
 * @property uuid The document now referring to that location, new or not.
 * @property forgotten The linked documents removed to stay within the limit.
 */
data class LinkedRegistration(val uuid: String, val forgotten: List<LinkedDocumentRef>)

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
        insert(pendingPages(documentId = id, count = document.pageCount ?: 0))
        return id
    }

    /** The oldest document of the library with this content, the bin left out. */
    @Query(
        "SELECT * FROM library_documents WHERE content_hash = :contentHash " +
            "ORDER BY created_at, id LIMIT 1"
    )
    abstract suspend fun findInLibraryByHash(contentHash: String): DocumentEntity?

    // --- documents of other apps ---

    /**
     * Refers to the location of [candidate]: through the document that already does, or by adding
     * [candidate]. Then forgets the linked documents used longest ago, until [keep] are left. In
     * one transaction, so that the limit is never seen exceeded, and nothing is forgotten for a
     * document that was not added after all.
     *
     * A linked document has activity, like any other, and no pages: its text is not read.
     */
    @Transaction
    open suspend fun registerLinked(candidate: DocumentEntity, keep: Int): LinkedRegistration {
        val uri = checkNotNull(candidate.uri) { "A linked document needs a location" }
        val known = linkedAt(uri)

        val uuid =
            if (known != null) {
                // Lent again, perhaps on other terms. Written only when it changed: a write here
                // is heard by every list of the library.
                if (known.hasPersistedPermission != candidate.hasPersistedPermission) {
                    setPersistedPermission(
                        known.uuid,
                        candidate.hasPersistedPermission == true,
                        candidate.updatedAt,
                    )
                }
                known.uuid
            } else {
                val id = insert(candidate)
                insert(
                    DocumentActivityEntity(
                        documentId = id,
                        lastOpenedAt = null,
                        lastActivityAt = candidate.createdAt,
                        readingPage = null,
                        readingOffset = null,
                        availability = DocumentAvailability.UNKNOWN,
                        availabilityCheckedAt = null,
                    )
                )
                candidate.uuid
            }

        // The one just registered is not among them even when it is the oldest: forgetting it
        // would undo what was asked.
        val forgotten = linkedBeyond(keep).filter { it.uuid != uuid }
        forgotten.forEach { deleteLinked(it.uuid) }

        return LinkedRegistration(uuid, forgotten)
    }

    /**
     * Removes a linked document, with its activity.
     *
     * @return Where it was, or `null` when there is no linked document with this [uuid].
     */
    @Transaction
    open suspend fun forgetLinked(uuid: String): String? {
        val uri = uriOfLinked(uuid) ?: return null
        deleteLinked(uuid)
        return uri
    }

    /**
     * Makes a linked document one the app keeps, and gives it the pages a kept document has. The
     * row is changed, not replaced: what hangs from it, its activity and its reading position, is
     * the same document's before and after.
     *
     * @return Whether there was a linked document with this [uuid] to change.
     */
    @Transaction
    open suspend fun keepLinkedInLibrary(
        uuid: String,
        filePath: String,
        sizeBytes: Long,
        contentHash: String,
        pageCount: Int?,
        ocrEnabled: Boolean,
        at: Long,
    ): Boolean {
        val id = idOfLinked(uuid) ?: return false
        makeManaged(uuid, filePath, sizeBytes, contentHash, pageCount, ocrEnabled, at)
        insert(pendingPages(documentId = id, count = pageCount ?: 0))
        return true
    }

    @Query("SELECT id FROM documents WHERE uuid = :uuid AND custody = 'LINKED'")
    protected abstract suspend fun idOfLinked(uuid: String): Long?

    /**
     * In one statement, because the row has to satisfy the custody trigger at every step: a
     * document with both a file and a location, or with neither, is refused. `source_uri = uri`
     * reads the location the row had before this statement.
     */
    @Query(
        """UPDATE documents
        SET custody = 'MANAGED', origin = 'IMPORT', file_path = :filePath,
            source_uri = uri, uri = NULL, has_persisted_permission = NULL,
            size_bytes = :sizeBytes, content_hash = :contentHash, page_count = :pageCount,
            is_encrypted = 0, ocr_enabled = :ocrEnabled, updated_at = :at, content_updated_at = :at
        WHERE uuid = :uuid AND custody = 'LINKED'"""
    )
    protected abstract suspend fun makeManaged(
        uuid: String,
        filePath: String,
        sizeBytes: Long,
        contentHash: String,
        pageCount: Int?,
        ocrEnabled: Boolean,
        at: Long,
    ): Int

    @Query("SELECT * FROM documents WHERE uri = :uri AND custody = 'LINKED'")
    protected abstract suspend fun linkedAt(uri: String): DocumentEntity?

    @Query("SELECT uri FROM documents WHERE uuid = :uuid AND custody = 'LINKED'")
    protected abstract suspend fun uriOfLinked(uuid: String): String?

    @Query(
        "UPDATE documents SET has_persisted_permission = :held, updated_at = :at " +
            "WHERE uuid = :uuid AND custody = 'LINKED'"
    )
    protected abstract suspend fun setPersistedPermission(uuid: String, held: Boolean, at: Long)

    /** The linked documents past the first [keep], counted from the one used last. */
    @Query(
        """SELECT d.uuid, d.uri
        FROM documents d JOIN document_activity a ON a.document_id = d.id
        WHERE d.custody = 'LINKED'
        ORDER BY a.last_activity_at DESC, d.id DESC
        LIMIT -1 OFFSET :keep"""
    )
    protected abstract suspend fun linkedBeyond(keep: Int): List<LinkedDocumentRef>

    /**
     * Only ever a linked document: the custody is part of the statement, so that no mistake above
     * it can make this delete a document the app keeps.
     */
    @Query("DELETE FROM documents WHERE uuid = :uuid AND custody = 'LINKED'")
    protected abstract suspend fun deleteLinked(uuid: String): Int

    /**
     * @param contentChanged Whether the file is not the one it was, which is when its content is
     *   dated again.
     */
    @Query(
        """UPDATE documents
        SET size_bytes = :sizeBytes, content_hash = :contentHash, page_count = :pageCount,
            is_encrypted = :isEncrypted, updated_at = :at,
            content_updated_at = CASE WHEN :contentChanged THEN :at ELSE content_updated_at END
        WHERE uuid = :uuid AND custody = 'LINKED'"""
    )
    abstract suspend fun describeLinked(
        uuid: String,
        sizeBytes: Long?,
        contentHash: String?,
        pageCount: Int?,
        isEncrypted: Boolean,
        contentChanged: Boolean,
        at: Long,
    ): Int

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
