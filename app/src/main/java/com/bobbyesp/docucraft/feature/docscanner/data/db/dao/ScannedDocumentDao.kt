package com.bobbyesp.docucraft.feature.docscanner.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.bobbyesp.docucraft.core.data.local.db.BaseDao
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.ScannedDocumentEntity
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.ScannedDocumentEntityWithMatchInfo
import kotlinx.coroutines.flow.Flow

@Dao
interface ScannedDocumentDao : BaseDao<ScannedDocumentEntity> {

    // Basic CRUD

    @Query("SELECT * FROM scanned_documents WHERE uuid = :uuid")
    suspend fun getByUuid(uuid: String): ScannedDocumentEntity?

    @Query("SELECT * FROM scanned_documents WHERE path = :path")
    suspend fun getByPath(path: String): ScannedDocumentEntity?

    @Query("SELECT * FROM scanned_documents ORDER BY createdTimestamp DESC")
    fun observeDocuments(): Flow<List<ScannedDocumentEntity>>

    @Query("""
        SELECT * FROM scanned_documents 
        WHERE createdTimestamp BETWEEN :startTime AND :endTime 
        ORDER BY createdTimestamp DESC
    """)
    suspend fun getWithinTimeRange(
        startTime: Long,
        endTime: Long
    ): List<ScannedDocumentEntity>

    @Query("DELETE FROM scanned_documents WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("DELETE FROM scanned_documents WHERE path = :path")
    suspend fun deleteByPath(path: String): Int

    @Query("DELETE FROM scanned_documents")
    suspend fun clear(): Int

    // FTS

    /**
     * The query has to come formatted
     */
    @Query("""
        SELECT sd.* 
        FROM scanned_documents sd
        JOIN scanned_documents_fts fts ON sd.rowid = fts.rowid
        WHERE scanned_documents_fts MATCH :query
        ORDER BY sd.createdTimestamp DESC
    """)
    suspend fun searchDocumentsFts(
        query: String
    ): List<ScannedDocumentEntity>


    //FTS with match info

    @Query("""
        SELECT sd.*, matchinfo(scanned_documents_fts) AS matchInfo
        FROM scanned_documents sd
        JOIN scanned_documents_fts 
            ON sd.rowid = scanned_documents_fts.rowid
        WHERE scanned_documents_fts MATCH :query
    """)
    suspend fun searchDocumentsWithMatchInfo(
        query: String
    ): List<ScannedDocumentEntityWithMatchInfo>
}