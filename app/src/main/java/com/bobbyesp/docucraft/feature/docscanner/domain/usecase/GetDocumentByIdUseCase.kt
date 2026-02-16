package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedPdf
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.ScannedDocumentsRepository

/** Use case for retrieving a single PDF by its ID. Single responsibility: fetch one PDF entity. */
class GetDocumentByIdUseCase(private val repository: ScannedDocumentsRepository) {
    suspend operator fun invoke(pdfId: String): ScannedPdf {
        require(pdfId.isNotBlank()) { "PDF ID cannot be blank" }
        return repository.getDocument(pdfId)
    }
}
