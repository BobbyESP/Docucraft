package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.LocalDocumentsRepository

/** Use case for retrieving a single document by its ID. Single responsibility: fetch one document entity. */
class GetDocumentByIdUseCase(private val repository: LocalDocumentsRepository) {
    suspend operator fun invoke(pdfId: String): ScannedDocument {
        require(pdfId.isNotBlank()) { "PDF ID cannot be blank" }
        return repository.getDocument(pdfId)
    }
}
