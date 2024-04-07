package com.bobbyesp.docucraft.data.local.db.daos

import androidx.paging.PagingSource
import androidx.room.Query
import com.bobbyesp.docucraft.data.local.db.BaseDao
import com.bobbyesp.docucraft.data.local.db.entity.SavedPdfEntity
import kotlinx.coroutines.flow.Flow

interface SavedPDFsDao: BaseDao<SavedPdfEntity> {
    @Query("SELECT * FROM saved_pdfs ORDER BY savedTimestamp DESC")
    fun loadAllPDFsPaged(): PagingSource<Int, SavedPdfEntity>

    @Query("SELECT * from saved_pdfs")
    suspend fun getAllSavedPDFs(): List<SavedPdfEntity>

    @Query("SELECT * from saved_pdfs")
    fun getAllSavedPDFsAsFlow(): Flow<List<SavedPdfEntity>>

    @Query("SELECT * FROM saved_pdfs ORDER BY savedTimestamp DESC")
    suspend fun getAllPDFsByTimestamp(): List<SavedPdfEntity>

    @Query("SELECT * FROM saved_pdfs WHERE id = :pdfId")
    suspend fun getSavedPDFById(pdfId: Int): SavedPdfEntity

    @Query("DELETE FROM saved_pdfs WHERE id = :pdfId")
    fun deletePDFById(pdfId: Int)
}