package com.bobbyesp.docucraft.feature.docscanner.data.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import com.bobbyesp.docucraft.core.data.local.db.BaseDao
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.ScannedDocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScannedDocumentDao : BaseDao<ScannedDocumentEntity> {
    @Query("SELECT * FROM scanned_documents ORDER BY createdTimestamp DESC LIMIT :limit OFFSET :offset")
    fun fetchPaginated(offset: Int = 0, limit: Int = 20): PagingSource<Int, ScannedDocumentEntity>

    @Query("SELECT * FROM scanned_documents WHERE id = :id")
    suspend fun getById(id: String): ScannedDocumentEntity?

    @Query("SELECT * FROM scanned_documents WHERE path = :path")
    suspend fun getByPath(path: String): ScannedDocumentEntity?

    @Query("SELECT * FROM scanned_documents ORDER BY createdTimestamp DESC")
    fun observeDocuments(): Flow<List<ScannedDocumentEntity>>

    @Query("SELECT * FROM scanned_documents WHERE filename LIKE '%' || :searchQuery || '%'")
    suspend fun searchByFilename(searchQuery: String): List<ScannedDocumentEntity>

    @Query("SELECT * FROM scanned_documents WHERE description LIKE '%' || :searchQuery || '%'")
    suspend fun searchByDescription(searchQuery: String): List<ScannedDocumentEntity>

    @Query("SELECT COUNT(*) FROM scanned_documents") suspend fun getScannedDocumentsCount(): Int

    @Query("DELETE FROM scanned_documents WHERE path = :path")
    suspend fun deleteByPath(path: String): Int

    @Query("DELETE FROM scanned_documents WHERE id = :id") suspend fun deleteById(id: String): Int

    @Query("DELETE FROM scanned_documents") suspend fun clear(): Int

    @Query(
        "SELECT * FROM scanned_documents WHERE createdTimestamp BETWEEN :startTime AND :endTime ORDER BY createdTimestamp DESC"
    )
    suspend fun getWithinTimeRange(startTime: Long, endTime: Long): List<ScannedDocumentEntity>

    @Query(
        """
        SELECT * FROM scanned_documents 
        WHERE (:searchQuery IS NULL OR 
               title LIKE '%' || :searchQuery || '%' OR 
               filename LIKE '%' || :searchQuery || '%' OR 
               description LIKE '%' || :searchQuery || '%')
        AND (:startTime IS NULL OR createdTimestamp >= :startTime)
        AND (:endTime IS NULL OR createdTimestamp <= :endTime)
        AND (:minPages IS NULL OR pageCount >= :minPages)
        AND (:minSize IS NULL OR fileSize >= :minSize)
        ORDER BY createdTimestamp DESC
    """
    )
    suspend fun searchDocuments(
        searchQuery: String? = null,
        startTime: Long? = null,
        endTime: Long? = null,
        minPages: Int? = null,
        minSize: Long? = null,
    ): List<ScannedDocumentEntity>

    @Query("UPDATE scanned_documents SET title = :title, description = :description WHERE id = :id")
    suspend fun updateDocumentDetails(id: String, title: String?, description: String?): Int
}
