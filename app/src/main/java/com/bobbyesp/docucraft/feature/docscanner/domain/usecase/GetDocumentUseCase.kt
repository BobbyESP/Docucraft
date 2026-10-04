/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.DocumentsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GetDocumentUseCase(private val repository: DocumentsRepository) {
    suspend operator fun invoke(documentUuid: String): Document =
        withContext(Dispatchers.IO) {
            require(documentUuid.isNotBlank()) { "Document ID cannot be blank" }
            repository.getDocument(documentUuid)
        }
}
