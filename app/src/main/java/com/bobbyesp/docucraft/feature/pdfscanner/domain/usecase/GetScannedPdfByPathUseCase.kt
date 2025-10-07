package com.bobbyesp.docucraft.feature.pdfscanner.domain.usecase

import android.net.Uri
import com.bobbyesp.docucraft.feature.pdfscanner.domain.model.ScannedPdf
import com.bobbyesp.docucraft.feature.pdfscanner.domain.repository.ScannedPdfRepository

/** Use case for retrieving a PDF by its file path. Single responsibility: fetch PDF by location. */
class GetScannedPdfByPathUseCase(private val repository: ScannedPdfRepository) {
    suspend operator fun invoke(pdfPath: Uri): ScannedPdf {
        return repository.getScannedPdfByPath(pdfPath)
    }
}
