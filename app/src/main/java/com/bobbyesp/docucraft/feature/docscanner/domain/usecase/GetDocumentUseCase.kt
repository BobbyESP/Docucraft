/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.LocalDocumentsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GetDocumentUseCase(private val repository: LocalDocumentsRepository) {
    suspend operator fun invoke(documentUuid: String): ScannedDocument =
        withContext(Dispatchers.IO) {
            require(documentUuid.isNotBlank()) { "Document ID cannot be blank" }
            repository.getDocument(documentUuid)
        }
}
