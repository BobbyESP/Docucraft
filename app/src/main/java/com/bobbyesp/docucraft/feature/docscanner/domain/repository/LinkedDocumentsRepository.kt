/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.repository

import com.bobbyesp.docucraft.feature.docscanner.domain.storage.StoredDocument
import com.bobbyesp.scanner.ContentRef

/**
 * A document of another app, about to be referred to.
 *
 * @property location Where the other app says it is. This is what identifies it: the same location
 *   is the same document, however it is called.
 * @property originalName What the other app calls it, without extension.
 * @property hasPersistedPermission Whether the permission to read it could be kept.
 */
data class NewLinkedDocument(
    val location: ContentRef,
    val originalName: String,
    val hasPersistedPermission: Boolean,
)

/**
 * @property uuid The document's, new or the one it already had.
 * @property forgotten Where the documents that had to be forgotten to make room were.
 */
data class LinkRegistration(val uuid: String, val forgotten: List<ContentRef>)

/**
 * What could be learnt of another app's document by reading it. Each is `null` when it could not be
 * learnt this time, which says nothing about what was learnt before.
 *
 * @property contentHash SHA-256 of its bytes, in hexadecimal.
 */
data class LinkedDocumentFacts(
    val sizeBytes: Long?,
    val contentHash: String?,
    val pageCount: Int?,
    val isProtected: Boolean,
)

/**
 * The documents the catalogue only refers to: PDFs of other apps that were opened here. Nothing in
 * here touches their files, which are not the app's to change or delete.
 */
interface LinkedDocumentsRepository {

    /**
     * Refers to the document at [NewLinkedDocument.location]: the one the catalogue already has for
     * that location, or a new one. Opening the same location twice is one document.
     *
     * No more than [limit] are kept. When a new one goes over, the ones used longest ago are
     * forgotten, and returned so that what was held for them can be let go.
     */
    suspend fun register(link: NewLinkedDocument, limit: Int): LinkRegistration

    /**
     * Forgets the linked document with this [uuid]: the reference, never the file.
     *
     * @return Where it was, or `null` when the catalogue has no linked document with this uuid. A
     *   document the app keeps is never forgotten through here.
     */
    suspend fun forget(uuid: String): ContentRef?

    /** Notes [facts] about the linked document with this [uuid]. Nothing if there is none. */
    suspend fun describe(uuid: String, facts: LinkedDocumentFacts)

    /**
     * Turns the linked document with this [uuid] into one the app keeps, whose file is [stored]: an
     * imported document of the library, with a page for each of its pages. All of it or none of it.
     *
     * It is the same document, changed in place: its uuid, when it was opened and where it was left
     * stay as they were, and where it came from is remembered.
     *
     * @param recognizeText Whether the text of its image-only pages is to be recognized, which only
     *   a document the app keeps can have.
     * @return `false` when the catalogue has no linked document with this uuid, and nothing was
     *   changed.
     */
    suspend fun keepInLibrary(uuid: String, stored: StoredDocument, recognizeText: Boolean): Boolean
}
