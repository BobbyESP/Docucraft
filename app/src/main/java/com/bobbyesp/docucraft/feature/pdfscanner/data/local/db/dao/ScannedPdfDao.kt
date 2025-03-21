package com.bobbyesp.docucraft.feature.pdfscanner.data.local.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import com.bobbyesp.docucraft.core.data.local.db.BaseDao
import com.bobbyesp.docucraft.feature.pdfscanner.data.local.db.entity.ScannedPdfEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScannedPdfDao: BaseDao<ScannedPdfEntity> {
    @Query("SELECT * FROM scanned_pdfs ORDER BY createdTimestamp DESC LIMIT :limit OFFSET :offset")
    fun fetchPaginated(offset: Int = 0, limit: Int = 20): PagingSource<Int, ScannedPdfEntity>

    @Query("SELECT * FROM scanned_pdfs WHERE id = :id")
    suspend fun fetchById(id: String): ScannedPdfEntity?

    @Query("SELECT * FROM scanned_pdfs ORDER BY createdTimestamp DESC")
    suspend fun getAllPdfs(): List<ScannedPdfEntity>

    @Query("SELECT * FROM scanned_pdfs ORDER BY createdTimestamp DESC")
    fun getAllPdfsFlow(): Flow<List<ScannedPdfEntity>>

    @Query("SELECT * FROM scanned_pdfs ORDER BY createdTimestamp DESC LIMIT :limit")
    suspend fun getRecentPdfs(limit: Int = 5): List<ScannedPdfEntity>

    @Query("SELECT * FROM scanned_pdfs WHERE filename LIKE '%' || :searchQuery || '%'")
    suspend fun searchPdfsByName(searchQuery: String): List<ScannedPdfEntity>

    @Query("SELECT * FROM scanned_pdfs WHERE pageCount >= :minPages")
    suspend fun getPdfsByMinPageCount(minPages: Int): List<ScannedPdfEntity>

    @Query("SELECT * FROM scanned_pdfs WHERE fileSize > :minSizeBytes")
    suspend fun getPdfsByMinSize(minSizeBytes: Long): List<ScannedPdfEntity>

    @Query("SELECT COUNT(*) FROM scanned_pdfs")
    suspend fun getPdfCount(): Int

    @Query("DELETE FROM scanned_pdfs WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("DELETE FROM scanned_pdfs")
    suspend fun deleteAll(): Int

    @Query("SELECT SUM(fileSize) FROM scanned_pdfs")
    suspend fun getTotalPdfSize(): Long?

    @Query("SELECT * FROM scanned_pdfs WHERE createdTimestamp BETWEEN :startTime AND :endTime")
    suspend fun getPdfsBetweenDates(startTime: Long, endTime: Long): List<ScannedPdfEntity>
}