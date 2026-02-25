package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.LocalDocumentsRepository

/**
 * Use case for searching PDFs by title or description. Single responsibility: perform search
 * operation.
 */
class SearchDocumentsUseCase(private val repository: LocalDocumentsRepository) {
    suspend operator fun invoke(query: String): List<ScannedDocument> {
        return repository.searchDocuments(query)
    }
}
