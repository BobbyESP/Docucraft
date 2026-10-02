/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner

import com.bobbyesp.docucraft.feature.docscanner.domain.model.DocumentAvailability
import com.bobbyesp.docucraft.feature.docscanner.domain.model.RecentDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.DocumentActivityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** What was noted about documents, kept in memory in the order it was noted. */
class FakeDocumentActivityRepository(recents: List<RecentDocument> = emptyList()) :
    DocumentActivityRepository {

    val recents = MutableStateFlow(recents)

    /** The uuids noted as opened, in order. */
    val opened = mutableListOf<String>()

    /** What was said of each document's file, in order. */
    val availability = mutableListOf<Pair<String, DocumentAvailability>>()

    override fun observeRecents(limit: Int): Flow<List<RecentDocument>> = recents.map {
        it.take(limit)
    }

    override suspend fun recordOpened(uuid: String) {
        opened += uuid
    }

    override suspend fun recordAvailability(uuid: String, availability: DocumentAvailability) {
        this.availability += uuid to availability
    }
}
