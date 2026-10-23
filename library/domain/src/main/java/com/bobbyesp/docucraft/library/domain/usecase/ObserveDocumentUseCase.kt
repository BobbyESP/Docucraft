/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.library.domain.usecase

import com.bobbyesp.docucraft.library.domain.model.Document
import com.bobbyesp.docucraft.library.domain.repository.DocumentsRepository
import kotlinx.coroutines.flow.Flow

/** Follows a single document, emitting `null` once it is gone. */
class ObserveDocumentUseCase(private val repository: DocumentsRepository) {
    operator fun invoke(uuid: String): Flow<Document?> = repository.observeDocument(uuid)
}
