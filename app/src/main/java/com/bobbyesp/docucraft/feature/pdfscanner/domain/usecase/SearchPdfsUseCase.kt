package com.bobbyesp.docucraft.feature.pdfscanner.domain.usecase

import com.bobbyesp.docucraft.feature.pdfscanner.domain.model.ScannedPdf
import com.bobbyesp.docucraft.feature.pdfscanner.domain.repository.ScannedPdfRepository

/**
 * Use case for searching PDFs by title or description. Single responsibility: perform search
 * operation.
 */
class SearchPdfsUseCase(private val repository: ScannedPdfRepository) {
    suspend operator fun invoke(query: String): List<ScannedPdf> {
        if (query.isBlank()) return emptyList()
        return repository.searchPdfsByTitleOrDescription(query)
    }
}
