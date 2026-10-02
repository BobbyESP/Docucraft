/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.DocumentsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.storage.DocumentStorage
import com.bobbyesp.docucraft.feature.docscanner.domain.storage.DocumentThumbnails

/**
 * Removes a document from the catalogue and from storage, and its previews from their cache.
 *
 * The catalogue goes first: a row pointing at a file that is gone is worse than a file nothing
 * points at, and only the first is visible to the user.
 */
class DeleteDocumentUseCase(
    private val repository: DocumentsRepository,
    private val storage: DocumentStorage,
    private val thumbnails: DocumentThumbnails,
) {
    suspend operator fun invoke(document: Document) {
        repository.deleteDocument(document.location)

        storage.delete(document.location)
        thumbnails.discard(document.uuid)
    }
}
