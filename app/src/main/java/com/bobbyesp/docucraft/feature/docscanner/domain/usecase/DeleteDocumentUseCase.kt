/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.LocalDocumentsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.storage.DocumentStorage

/**
 * Removes a document from the catalogue and from storage.
 *
 * The catalogue goes first: a row pointing at a file that is gone is worse than a file nothing
 * points at, and only the first is visible to the user.
 */
class DeleteDocumentUseCase(
    private val repository: LocalDocumentsRepository,
    private val storage: DocumentStorage,
) {
    suspend operator fun invoke(document: ScannedDocument) {
        repository.deleteDocument(document.location)

        storage.delete(document.location)
        // The preview was never cleaned up before, so previews of deleted documents piled up.
        document.thumbnail?.let { storage.delete(it) }
    }
}
