/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.repository

import com.bobbyesp.docucraft.feature.docscanner.domain.model.NewScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.scanner.ContentRef
import kotlinx.coroutines.flow.Flow

/**
 * The catalogue of scanned documents: what the app knows about them, as opposed to where their
 * bytes live, which is `DocumentStorage`'s business.
 */
interface LocalDocumentsRepository {

    /**
     * Every catalogued document, newest first, emitted again on every change — a scan saved, a
     * document deleted, a title edited.
     *
     * The flow does not end on its own.
     */
    fun observeDocuments(): Flow<List<ScannedDocument>>

    /**
     * Searches filename, title and description at once, so the UI can offer a single search box.
     *
     * Results come back newest first, not by relevance.
     *
     * @param query Free text. An empty or blank query matches nothing.
     * @return The matching documents, or an empty list.
     */
    suspend fun searchDocuments(query: String): List<ScannedDocument>

    /**
     * @return The document with this [uuid].
     * @throws NoSuchElementException If the catalogue holds no such document.
     */
    suspend fun getDocument(uuid: String): ScannedDocument

    /**
     * Adds a freshly stored document to the catalogue.
     *
     * The document is expected to already exist at [NewScannedDocument.location]. This records it,
     * gives it an identity, and causes [observeDocuments] to emit again.
     */
    suspend fun saveDocument(document: NewScannedDocument)

    /**
     * Replaces the two fields the user can write.
     *
     * Both are replaced outright: passing `null` **clears** that field rather than leaving it
     * alone, which is what the edit dialog needs when someone empties a box.
     *
     * @throws NoSuchElementException If the catalogue holds no document with this [uuid].
     */
    suspend fun modifyFields(uuid: String, title: String?, description: String?)

    /**
     * Forgets the document stored at [location].
     *
     * Only the catalogue entry goes; removing the document itself is storage's job.
     *
     * @throws IllegalArgumentException If the catalogue holds no document there.
     */
    suspend fun deleteDocument(location: ContentRef)
}
