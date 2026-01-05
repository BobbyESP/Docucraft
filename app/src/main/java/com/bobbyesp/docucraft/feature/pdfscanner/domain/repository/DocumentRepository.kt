package com.bobbyesp.docucraft.feature.pdfscanner.domain.repository

import com.bobbyesp.docucraft.core.domain.model.ScannedDocument
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing the persistence of scanned documents.
 */
interface DocumentRepository {
    /**
     * Saves a scanned document to the local storage/database.
     */
    suspend fun saveDocument(document: ScannedDocument): Long

    /**
     * Retrieves all scanned documents.
     */
    fun getAllDocuments(): Flow<List<ScannedDocument>>

    /**
     * Deletes a document.
     */
    suspend fun deleteDocument(document: ScannedDocument)
}
