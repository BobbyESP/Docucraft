package com.bobbyesp.docucraft.feature.docscanner.data.local.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import com.bobbyesp.docucraft.core.data.local.db.BaseDao
import com.bobbyesp.docucraft.feature.docscanner.data.local.db.entity.ScannedPdfEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScannedPdfDao : BaseDao<ScannedPdfEntity> {
    @Query("SELECT * FROM scanned_pdfs ORDER BY createdTimestamp DESC LIMIT :limit OFFSET :offset")
    fun fetchPaginated(offset: Int = 0, limit: Int = 20): PagingSource<Int, ScannedPdfEntity>

    @Query("SELECT * FROM scanned_pdfs WHERE id = :id")
    suspend fun fetchById(id: String): ScannedPdfEntity?

    @Query("SELECT * FROM scanned_pdfs WHERE path = :path")
    suspend fun fetchByPath(path: String): ScannedPdfEntity?

    @Query("SELECT * FROM scanned_pdfs ORDER BY createdTimestamp DESC")
    fun observeAllPdfs(): Flow<List<ScannedPdfEntity>>

    @Query("SELECT * FROM scanned_pdfs ORDER BY createdTimestamp DESC LIMIT :limit")
    suspend fun fetchRecentPdfs(limit: Int = 5): List<ScannedPdfEntity>

    @Query("SELECT * FROM scanned_pdfs WHERE filename LIKE '%' || :searchQuery || '%'")
    suspend fun searchPdfsByFilename(searchQuery: String): List<ScannedPdfEntity>

    @Query("SELECT * FROM scanned_pdfs WHERE description LIKE '%' || :searchQuery || '%'")
    suspend fun searchPdfsByDescription(searchQuery: String): List<ScannedPdfEntity>

    @Query("SELECT COUNT(*) FROM scanned_pdfs") suspend fun fetchPdfCount(): Int

    @Query("DELETE FROM scanned_pdfs WHERE path = :path")
    suspend fun deleteByPath(path: String): Int

    @Query("DELETE FROM scanned_pdfs WHERE id = :id") suspend fun deleteById(id: String): Int

    @Query("DELETE FROM scanned_pdfs") suspend fun deleteAll(): Int

    @Query(
        "SELECT * FROM scanned_pdfs WHERE createdTimestamp BETWEEN :startTime AND :endTime ORDER BY createdTimestamp DESC"
    )
    suspend fun fetchPdfsInDateRange(startTime: Long, endTime: Long): List<ScannedPdfEntity>

    @Query(
        """
        SELECT * FROM scanned_pdfs 
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
    ): List<ScannedPdfEntity>

    @Query("UPDATE scanned_pdfs SET title = :title, description = :description WHERE id = :id")
    suspend fun updateTitleAndDescription(id: String, title: String?, description: String?): Int

    @Query(
        "SELECT * FROM scanned_pdfs WHERE title LIKE '%' || :searchQuery || '%' OR description LIKE '%' || :searchQuery || '%'"
    )
    suspend fun searchPdfsByTitleOrDescription(searchQuery: String): List<ScannedPdfEntity>
}
