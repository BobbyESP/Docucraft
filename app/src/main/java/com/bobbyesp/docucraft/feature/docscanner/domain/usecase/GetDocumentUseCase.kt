package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import android.net.Uri
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.LocalDocumentsRepository

class GetDocumentUseCase(
    private val repository: LocalDocumentsRepository
) {
    suspend operator fun invoke(documentUuid: String): ScannedDocument {
        require(documentUuid.isNotBlank()) { "Document ID cannot be blank" }
        return repository.getDocument(documentUuid)
    }

    suspend operator fun invoke(documentPath: Uri): ScannedDocument {
        return repository.getDocument(documentPath)
    }
}