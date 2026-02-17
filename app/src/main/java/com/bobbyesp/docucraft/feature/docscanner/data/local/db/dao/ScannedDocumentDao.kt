package com.bobbyesp.docucraft.feature.docscanner.data.local.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import com.bobbyesp.docucraft.core.data.local.db.BaseDao
import com.bobbyesp.docucraft.feature.docscanner.data.local.db.entity.ScannedDocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScannedDocumentDao : BaseDao<ScannedDocumentEntity> {
    @Query("SELECT * FROM scanned_documents ORDER BY createdTimestamp DESC LIMIT :limit OFFSET :offset")
    fun fetchPaginated(offset: Int = 0, limit: Int = 20): PagingSource<Int, ScannedDocumentEntity>

    @Query("SELECT * FROM scanned_documents WHERE id = :id")
    suspend fun fetchById(id: String): ScannedDocumentEntity?

    @Query("SELECT * FROM scanned_documents WHERE path = :path")
    suspend fun fetchByPath(path: String): ScannedDocumentEntity?

    @Query("SELECT * FROM scanned_documents ORDER BY createdTimestamp DESC")
    fun observeAllPdfs(): Flow<List<ScannedDocumentEntity>>

    @Query("SELECT * FROM scanned_documents ORDER BY createdTimestamp DESC LIMIT :limit")
    suspend fun fetchRecentPdfs(limit: Int = 5): List<ScannedDocumentEntity>

    @Query("SELECT * FROM scanned_documents WHERE filename LIKE '%' || :searchQuery || '%'")
    suspend fun searchPdfsByFilename(searchQuery: String): List<ScannedDocumentEntity>

    @Query("SELECT * FROM scanned_documents WHERE description LIKE '%' || :searchQuery || '%'")
    suspend fun searchPdfsByDescription(searchQuery: String): List<ScannedDocumentEntity>

    @Query("SELECT COUNT(*) FROM scanned_documents") suspend fun fetchPdfCount(): Int

    @Query("DELETE FROM scanned_documents WHERE path = :path")
    suspend fun deleteByPath(path: String): Int

    @Query("DELETE FROM scanned_documents WHERE id = :id") suspend fun deleteById(id: String): Int

    @Query("DELETE FROM scanned_documents") suspend fun deleteAll(): Int

    @Query(
        "SELECT * FROM scanned_documents WHERE createdTimestamp BETWEEN :startTime AND :endTime ORDER BY createdTimestamp DESC"
    )
    suspend fun fetchPdfsInDateRange(startTime: Long, endTime: Long): List<ScannedDocumentEntity>

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
    suspend fun searchPdfsWithFilters(
        searchQuery: String? = null,
        startTime: Long? = null,
        endTime: Long? = null,
        minPages: Int? = null,
        minSize: Long? = null,
    ): List<ScannedDocumentEntity>

    @Query("UPDATE scanned_documents SET title = :title, description = :description WHERE id = :id")
    suspend fun updateTitleAndDescription(id: String, title: String?, description: String?): Int

    @Query(
        "SELECT * FROM scanned_documents WHERE title LIKE '%' || :searchQuery || '%' OR description LIKE '%' || :searchQuery || '%'"
    )
    suspend fun searchPdfsByTitleOrDescription(searchQuery: String): List<ScannedDocumentEntity>
}
