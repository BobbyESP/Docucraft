/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.LocalDocumentsRepository
import kotlinx.coroutines.flow.Flow

class ObserveDocumentsUseCase(private val repository: LocalDocumentsRepository) {
    operator fun invoke(): Flow<List<ScannedDocument>> = repository.observeDocuments()
}
