package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import android.net.Uri
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.LocalDocumentsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GetDocumentUseCase(
    private val repository: LocalDocumentsRepository
) {
    suspend operator fun invoke(documentUuid: String): ScannedDocument = withContext(Dispatchers.IO) {
        require(documentUuid.isNotBlank()) { "Document ID cannot be blank" }
        repository.getDocument(documentUuid)
    }

    suspend operator fun invoke(documentPath: Uri): ScannedDocument = withContext(Dispatchers.IO) {
        repository.getDocument(documentPath)
    }
}