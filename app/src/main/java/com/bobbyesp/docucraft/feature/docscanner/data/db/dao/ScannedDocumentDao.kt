/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.bobbyesp.docucraft.core.data.local.db.BaseDao
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.ScannedDocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScannedDocumentDao : BaseDao<ScannedDocumentEntity> {

    @Query("SELECT * FROM scanned_documents WHERE uuid = :uuid")
    suspend fun getByUuid(uuid: String): ScannedDocumentEntity?

    /** Emits again whenever the row changes, and emits null once it is gone. */
    @Query("SELECT * FROM scanned_documents WHERE uuid = :uuid")
    fun observeByUuid(uuid: String): Flow<ScannedDocumentEntity?>

    @Query("SELECT * FROM scanned_documents ORDER BY createdTimestamp DESC")
    fun observeDocuments(): Flow<List<ScannedDocumentEntity>>

    @Query("DELETE FROM scanned_documents WHERE path = :path")
    suspend fun deleteByPath(path: String): Int

    /**
     * Full-text search. The query has to arrive already formatted for FTS.
     *
     * Results come back newest first rather than by relevance: ranking needs `matchinfo()` decoded
     * into a score, and that is not written yet.
     */
    @Query(
        """
        SELECT sd.*
        FROM scanned_documents sd
        JOIN scanned_documents_fts fts ON sd.rowid = fts.rowid
        WHERE scanned_documents_fts MATCH :query
        ORDER BY sd.createdTimestamp DESC
    """
    )
    suspend fun searchDocumentsFts(query: String): List<ScannedDocumentEntity>
}
