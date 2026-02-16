package com.bobbyesp.docucraft.feature.docscanner.data.local.repository

import android.content.Context
import android.net.Uri
import com.bobbyesp.docucraft.feature.docscanner.data.local.db.dao.ScannedPdfDao
import com.bobbyesp.docucraft.feature.docscanner.data.local.db.entity.ScannedPdfEntity
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedPdf
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedPdf.Companion.toModel
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.ScannedDocumentsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class ScannedDocumentsRepositoryImpl(
    private val context: Context,
    private val scannedPdfDao: ScannedPdfDao,
) : ScannedDocumentsRepository {

    override suspend fun observeDocuments(): Flow<List<ScannedPdf>> =
        scannedPdfDao
            .observeAllPdfs()
            .map { entities -> entities.map { it.toModel() } }
            .flowOn(Dispatchers.IO)

    override suspend fun searchDocuments(query: String): List<ScannedPdf> {
        if (query.isBlank()) {
            return emptyList()
        }

        // Search for name coincidences
        val nameResults = scannedPdfDao.searchPdfsByFilename(query)
        
        // Search for title or description coincidences
        val titleDescResults = scannedPdfDao.searchPdfsByTitleOrDescription(query)

        // Combine results and remove duplicates
        val combinedResults = (nameResults + titleDescResults).distinctBy { it.id }

        return combinedResults.map { it.toModel() }
    }

    override suspend fun getDocument(id: String): ScannedPdf {
        require(id.isNotEmpty()) { "document ID must not be empty" }
        val entity =
            scannedPdfDao.fetchById(id)
                ?: throw NoSuchElementException("No document found with ID: $id")
        return entity.toModel()
    }

    override suspend fun getDocument(path: Uri): ScannedPdf {
        val entity =
            scannedPdfDao.fetchByPath(path.toString())
                ?: throw NoSuchElementException("No document found with path: $path")
        return entity.toModel()
    }

    override suspend fun saveDocument(scannedPdf: ScannedPdfEntity) {
        try {
            scannedPdfDao.insert(scannedPdf)
        } catch (e: Exception) {
            throw RuntimeException("Failed to save document: ${e.message}", e)
        }
    }

    override suspend fun modifyFields(
        id: String,
        title: String?,
        description: String?,
    ) {
        require(id.isNotEmpty()) { "document ID must not be empty" }

        // If both are null, nothing to update
        if (title == null && description == null) return

        val updatedCount = scannedPdfDao.updateTitleAndDescription(id, title, description)

        if (updatedCount <= 0) {
            throw NoSuchElementException("No document found with ID: $id")
        }
    }

    override suspend fun deleteDocument(pdfPath: Uri) {
        // First remove from database to maintain referential integrity
        val deletedCount = scannedPdfDao.deleteByPath(pdfPath.toString())

        if (deletedCount <= 0) {
            throw IllegalArgumentException("No document found with path: $pdfPath")
        }
    }

    companion object {
        private const val TAG = "ScannedDocumentsRepository"
    }
}
