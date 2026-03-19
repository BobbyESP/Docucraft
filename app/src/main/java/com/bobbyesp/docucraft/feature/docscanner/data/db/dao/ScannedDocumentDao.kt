package com.bobbyesp.docucraft.feature.docscanner.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.bobbyesp.docucraft.core.data.local.db.BaseDao
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.ScannedDocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScannedDocumentDao : BaseDao<ScannedDocumentEntity> {
    @Query("SELECT * FROM scanned_documents WHERE id = :id")
    suspend fun getById(id: String): ScannedDocumentEntity?

    @Query("SELECT * FROM scanned_documents WHERE path = :path")
    suspend fun getByPath(path: String): ScannedDocumentEntity?

    @Query("SELECT * FROM scanned_documents ORDER BY createdTimestamp DESC")
    fun observeDocuments(): Flow<List<ScannedDocumentEntity>>

    @Query("UPDATE scanned_documents SET title = :title, description = :description WHERE id = :id")
    suspend fun updateDocumentDetails(id: String, title: String?, description: String?): Int

    @Query("DELETE FROM scanned_documents WHERE path = :path")
    suspend fun deleteByPath(path: String): Int

    @Query("DELETE FROM scanned_documents WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query(
        """
            SELECT * FROM scanned_documents d
            JOIN scanned_documents_fts fts ON d.id = fts.rowid
            WHERE (
        ( (d.title IS NOT NULL OR d.description IS NOT NULL) 
          AND scanned_documents_fts MATCH 'title:' || :query || '* OR description:' || :query || '*' )
        OR
        
        ( (d.title IS NULL AND d.description IS NULL)
          AND scanned_documents_fts MATCH 'filename:' || :query || '*' )
    )
    """
    )
    suspend fun searchDocumentsFts(
        query: String? = null,
    ): List<ScannedDocumentEntity>

    @Query(
        "SELECT * FROM scanned_documents WHERE createdTimestamp BETWEEN :startTime AND :endTime ORDER BY createdTimestamp DESC"
    )
    suspend fun getWithinTimeRange(startTime: Long, endTime: Long): List<ScannedDocumentEntity>

    @Query("DELETE FROM scanned_documents")
    suspend fun clear(): Int
}
