package com.bobbyesp.docucraft.feature.docscanner.data.local.repository

import android.content.Context
import android.net.Uri
import com.bobbyesp.docucraft.feature.docscanner.data.local.db.dao.ScannedDocumentDao
import com.bobbyesp.docucraft.feature.docscanner.data.local.db.entity.ScannedDocumentEntity
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument.Companion.toModel
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.ScannedDocumentsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class ScannedDocumentsRepositoryImpl(
    private val context: Context,
    private val scannedDocumentDao: ScannedDocumentDao,
) : ScannedDocumentsRepository {

    override suspend fun observeDocuments(): Flow<List<ScannedDocument>> =
        scannedDocumentDao
            .observeAllPdfs()
            .map { entities -> entities.map { it.toModel() } }
            .flowOn(Dispatchers.IO)

    override suspend fun searchDocuments(query: String): List<ScannedDocument> {
        if (query.isBlank()) {
            return emptyList()
        }

        // Search for name coincidences
        val nameResults = scannedDocumentDao.searchPdfsByFilename(query)
        
        // Search for title or description coincidences
        val titleDescResults = scannedDocumentDao.searchPdfsByTitleOrDescription(query)

        // Combine results and remove duplicates
        val combinedResults = (nameResults + titleDescResults).distinctBy { it.id }

        return combinedResults.map { it.toModel() }
    }

    override suspend fun getDocument(id: String): ScannedDocument {
        require(id.isNotEmpty()) { "document ID must not be empty" }
        val entity =
            scannedDocumentDao.fetchById(id)
                ?: throw NoSuchElementException("No document found with ID: $id")
        return entity.toModel()
    }

    override suspend fun getDocument(path: Uri): ScannedDocument {
        val entity =
            scannedDocumentDao.fetchByPath(path.toString())
                ?: throw NoSuchElementException("No document found with path: $path")
        return entity.toModel()
    }

    override suspend fun saveDocument(scannedPdf: ScannedDocumentEntity) {
        try {
            scannedDocumentDao.insert(scannedPdf)
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

        val updatedCount = scannedDocumentDao.updateTitleAndDescription(id, title, description)

        if (updatedCount <= 0) {
            throw NoSuchElementException("No document found with ID: $id")
        }
    }

    override suspend fun deleteDocument(pdfPath: Uri) {
        // First remove from database to maintain referential integrity
        val deletedCount = scannedDocumentDao.deleteByPath(pdfPath.toString())

        if (deletedCount <= 0) {
            throw IllegalArgumentException("No document found with path: $pdfPath")
        }
    }

    companion object {
        private const val TAG = "ScannedDocumentsRepository"
    }
}
