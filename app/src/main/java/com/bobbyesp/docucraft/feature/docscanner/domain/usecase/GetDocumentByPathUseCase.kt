package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import android.net.Uri
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedPdf
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.ScannedDocumentsRepository

/** Use case for retrieving a PDF by its file path. Single responsibility: fetch PDF by location. */
class GetDocumentByPathUseCase(private val repository: ScannedDocumentsRepository) {
    suspend operator fun invoke(pdfPath: Uri): ScannedPdf {
        return repository.getDocument(pdfPath)
    }
}
