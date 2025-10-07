package com.bobbyesp.docucraft.feature.pdfscanner.domain.usecase

import com.bobbyesp.docucraft.feature.pdfscanner.domain.model.ScannedPdf
import com.bobbyesp.docucraft.feature.pdfscanner.domain.repository.ScannedPdfRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case for retrieving all scanned PDFs as a flow. Follows Single Responsibility Principle -
 * handles only one business action.
 */
class GetAllScannedPdfsUseCase(private val repository: ScannedPdfRepository) {
    suspend operator fun invoke(): Flow<List<ScannedPdf>> {
        return repository.getAllScannedPdfsFlow()
    }
}
