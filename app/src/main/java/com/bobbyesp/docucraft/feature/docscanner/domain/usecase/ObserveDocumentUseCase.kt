/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.LocalDocumentsRepository
import kotlinx.coroutines.flow.Flow

/** Follows a single document, emitting `null` once it is gone. */
class ObserveDocumentUseCase(private val repository: LocalDocumentsRepository) {
    operator fun invoke(uuid: String): Flow<ScannedDocument?> = repository.observeDocument(uuid)
}
