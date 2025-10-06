package com.bobbyesp.docucraft.feature.pdfscanner.data.local.repository

import android.content.Context
import android.net.Uri
import com.bobbyesp.docucraft.feature.pdfscanner.data.local.db.dao.ScannedPdfDao
import com.bobbyesp.docucraft.feature.pdfscanner.data.local.db.entity.ScannedPdfEntity
import com.bobbyesp.docucraft.feature.pdfscanner.domain.model.ScannedPdf
import com.bobbyesp.docucraft.feature.pdfscanner.domain.model.ScannedPdf.Companion.toModel
import com.bobbyesp.docucraft.feature.pdfscanner.domain.repository.ScannedPdfRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class ScannedPdfRepositoryImpl(
    private val context: Context,
    private val scannedPdfDao: ScannedPdfDao,
) : ScannedPdfRepository {

    //TODO: Remove unnecessary functions

    override suspend fun getAllScannedPdfsFlow(): Flow<List<ScannedPdf>> =
        scannedPdfDao
            .observeAllPdfs()
            .map { entities -> entities.map { it.toModel() } }
            .flowOn(Dispatchers.IO)

    override suspend fun searchPdfsByFilename(query: String): List<ScannedPdf> {
        if (query.isEmpty()) {
            return emptyList()
        }
        val nameResults = scannedPdfDao.searchPdfsByFilename(query)
        return nameResults.map { it.toModel() }
    }

    override suspend fun searchPdfsByDescription(query: String): List<ScannedPdf> {
        if (query.isEmpty()) {
            return emptyList()
        }
        val descResults = scannedPdfDao.searchPdfsByDescription(query)
        return descResults.map { it.toModel() }
    }

    override suspend fun searchPdfsByTitleOrDescription(query: String): List<ScannedPdf> {
        if (query.isEmpty()) {
            return emptyList()
        }
        val nameResults = scannedPdfDao.searchPdfsByFilename(query)
        val titleDescResults = scannedPdfDao.searchPdfsByTitleOrDescription(query)

        return (nameResults + titleDescResults).distinctBy { it.id }.map { it.toModel() }
    }

    override suspend fun getScannedPdfById(pdfId: String): ScannedPdf {
        require(pdfId.isNotEmpty()) { "PDF ID must not be empty" }
        val entity =
            scannedPdfDao.fetchById(pdfId)
                ?: throw NoSuchElementException("No PDF found with ID: $pdfId")
        return entity.toModel()
    }

    override suspend fun getScannedPdfByPath(pdfPath: Uri): ScannedPdf {
        val entity =
            scannedPdfDao.fetchByPath(pdfPath.toString())
                ?: throw NoSuchElementException("No PDF found with path: $pdfPath")
        return entity.toModel()
    }

    override suspend fun savePdf(scannedPdf: ScannedPdfEntity) {
        try {
            scannedPdfDao.insert(scannedPdf)
        } catch (e: Exception) {
            throw RuntimeException("Failed to save PDF: ${e.message}", e)
        }
    }

    override suspend fun modifyTitleAndDescription(
        pdfId: String,
        title: String?,
        description: String?,
    ) {
        require(pdfId.isNotEmpty()) { "PDF ID must not be empty" }

        val updatedCount = scannedPdfDao.updateTitleAndDescription(pdfId, title, description)

        if (updatedCount <= 0) {
            throw NoSuchElementException("No PDF found with ID: $pdfId")
        }
    }

    override suspend fun deletePdf(pdfPath: Uri) {
        // First remove from database to maintain referential integrity
        val deletedCount = scannedPdfDao.deleteByPath(pdfPath.toString())

        if (deletedCount <= 0) {
            throw IllegalArgumentException("No PDF found with path: $pdfPath")
        }
    }

    companion object {
        private const val TAG = "ScannedPdfRepository"
    }
}
