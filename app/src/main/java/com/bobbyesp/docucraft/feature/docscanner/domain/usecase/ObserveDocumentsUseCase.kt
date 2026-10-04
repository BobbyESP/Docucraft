/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.DocumentsRepository
import kotlinx.coroutines.flow.Flow

class ObserveDocumentsUseCase(private val repository: DocumentsRepository) {
    operator fun invoke(): Flow<List<Document.Managed>> = repository.observeDocuments()
}
