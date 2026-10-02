/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.repository

import com.bobbyesp.docucraft.feature.docscanner.domain.model.DocumentAvailability
import com.bobbyesp.docucraft.feature.docscanner.domain.model.RecentDocument
import kotlinx.coroutines.flow.Flow

/**
 * What the user has done with the documents of the catalogue, as opposed to what the documents are:
 * when each was last opened and whether its file could be reached.
 *
 * Apart from `DocumentsRepository` because it is written all the time, by reading, and none of
 * those writes changes a document.
 */
interface DocumentActivityRepository {

    /**
     * The documents used last, most recent first: the app's own, unless they are in the bin, and
     * the ones that belong to other apps. A document that was saved and never opened counts from
     * when it was saved.
     *
     * Emitted again as documents are opened, saved or removed. The flow does not end on its own.
     *
     * @param limit How many, at most.
     */
    fun observeRecents(limit: Int): Flow<List<RecentDocument>>

    /** Notes that the document with this [uuid] has just been opened. Nothing if there is none. */
    suspend fun recordOpened(uuid: String)

    /**
     * Notes whether the file of the document with this [uuid] could be reached just now. Nothing if
     * there is no such document.
     */
    suspend fun recordAvailability(uuid: String, availability: DocumentAvailability)
}
