package com.bobbyesp.docucraft.feature.docscanner.data.repository

import android.net.Uri
import com.bobbyesp.docucraft.feature.docscanner.data.db.dao.ScannedDocumentDao
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.ScannedDocumentEntity
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument.Companion.toModel
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.LocalDocumentsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class LocalDocumentsRepositoryImpl(
    private val scannedDocumentDao: ScannedDocumentDao,
) : LocalDocumentsRepository {

    override suspend fun observeDocuments(): Flow<List<ScannedDocument>> =
        scannedDocumentDao
            .observeDocuments()
            .map { entities -> entities.map { it.toModel() } }
            .flowOn(Dispatchers.IO)

    override suspend fun searchDocuments(query: String): List<ScannedDocument> {
        if (query.isBlank()) {
            return emptyList()
        }

        val result = scannedDocumentDao.searchDocumentsFts(query = query)

        return result.map { it.toModel() }
    }

    override suspend fun getDocument(id: String): ScannedDocument {
        require(id.isNotEmpty()) { "Document ID must not be empty" }
        val entity =
            scannedDocumentDao.getById(id)
                ?: throw NoSuchElementException("No document found with ID: $id")
        return entity.toModel()
    }

    override suspend fun getDocument(path: Uri): ScannedDocument {
        val entity =
            scannedDocumentDao.getByPath(path.toString())
                ?: throw NoSuchElementException("No document found with path: $path")
        return entity.toModel()
    }

    override suspend fun saveDocument(scannedDocument: ScannedDocumentEntity) {
        try {
            scannedDocumentDao.insert(scannedDocument)
        } catch (e: Exception) {
            throw RuntimeException("Failed to save document: ${e.message}", e)
        }
    }

    override suspend fun modifyFields(
        id: String,
        title: String?,
        description: String?,
    ) {
        require(id.isNotEmpty()) { "Document ID must not be empty" }

        val updatedCount = scannedDocumentDao.updateDocumentDetails(id, title, description)

        if (updatedCount <= 0) {
            throw NoSuchElementException("No document found with ID: $id")
        }
    }

    override suspend fun deleteDocument(path: Uri) {
        // First remove from database to maintain referential integrity
        val deletedCount = scannedDocumentDao.deleteByPath(path.toString())

        if (deletedCount <= 0) {
            throw IllegalArgumentException("No document found with path: $path")
        }
    }

    companion object {
        private const val TAG = "ScannedDocumentsRepository"
    }
}
