package com.bobbyesp.docucraft.feature.pdfscanner.domain.usecase

import com.bobbyesp.docucraft.feature.pdfscanner.domain.model.ScannedPdf
import com.bobbyesp.docucraft.feature.pdfscanner.domain.repository.ScannedPdfRepository

/** Use case for retrieving a single PDF by its ID. Single responsibility: fetch one PDF entity. */
class GetScannedPdfByIdUseCase(private val repository: ScannedPdfRepository) {
    suspend operator fun invoke(pdfId: String): ScannedPdf {
        require(pdfId.isNotBlank()) { "PDF ID cannot be blank" }
        return repository.getScannedPdfById(pdfId)
    }
}
