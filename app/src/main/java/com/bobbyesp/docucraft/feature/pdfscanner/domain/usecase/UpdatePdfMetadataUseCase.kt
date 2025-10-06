package com.bobbyesp.docucraft.feature.pdfscanner.domain.usecase

import com.bobbyesp.docucraft.feature.pdfscanner.domain.repository.ScannedPdfRepository

/**
 * Use case for updating PDF title and description.
 * Single responsibility: modify PDF metadata.
 */
class UpdatePdfMetadataUseCase(
    private val repository: ScannedPdfRepository
) {
    suspend operator fun invoke(
        pdfId: String,
        title: String?,
        description: String?,
    ) {
        require(pdfId.isNotBlank()) { "PDF ID cannot be blank" }
        repository.modifyTitleAndDescription(pdfId, title, description)
    }
}

