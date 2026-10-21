/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.feature.docscanner.domain.model.DocumentAvailability
import com.bobbyesp.docucraft.feature.docscanner.domain.model.RecentDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.DocumentActivityRepository
import kotlinx.coroutines.flow.Flow

/** The documents used last, most recent first. */
class ObserveRecentDocumentsUseCase(private val activity: DocumentActivityRepository) {
    operator fun invoke(limit: Int): Flow<List<RecentDocument>> = activity.observeRecents(limit)
}

/**
 * Notes that a document has been opened, which is what puts it at the front of Recents. Called by
 * whoever shows the document, once each time it is opened.
 */
class RecordDocumentOpenedUseCase(private val activity: DocumentActivityRepository) {
    suspend operator fun invoke(documentUuid: String) = activity.recordOpened(documentUuid)
}

/**
 * Notes whether a document's file could be reached. Only whoever has just tried to read it knows,
 * so the catalogue is told rather than left to find out.
 */
/** The uuids of the library's documents whose file is not there, to show them as not found. */
class ObserveNotFoundDocumentsUseCase(private val activity: DocumentActivityRepository) {
    operator fun invoke(): Flow<Set<String>> = activity.observeNotFound()
}

class RecordDocumentAvailabilityUseCase(private val activity: DocumentActivityRepository) {
    suspend operator fun invoke(documentUuid: String, availability: DocumentAvailability) =
        activity.recordAvailability(documentUuid, availability)
}
