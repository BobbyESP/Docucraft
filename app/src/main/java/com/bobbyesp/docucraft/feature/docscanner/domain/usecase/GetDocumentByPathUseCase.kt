package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import android.net.Uri
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.LocalDocumentsRepository

/** Use case for retrieving a PDF by its file path. Single responsibility: fetch PDF by location. */
class GetDocumentByPathUseCase(private val repository: LocalDocumentsRepository) {
    suspend operator fun invoke(pdfPath: Uri): ScannedDocument {
        return repository.getDocument(pdfPath)
    }
}
