/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.repository

import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.model.NewScan
import kotlinx.coroutines.flow.Flow

/**
 * The catalogue of scanned documents: what the app knows about them, as opposed to where their
 * bytes live, which is `DocumentStorage`'s business.
 */
interface DocumentsRepository {

    /**
     * Every catalogued document, newest first, emitted again on every change — a scan saved, a
     * document deleted, a title edited.
     *
     * The flow does not end on its own.
     */
    fun observeDocuments(): Flow<List<Document.Managed>>

    /**
     * @return The document with this [uuid].
     * @throws NoSuchElementException If the catalogue holds no such document.
     */
    suspend fun getDocument(uuid: String): Document

    /**
     * One document, emitted again whenever it changes, and `null` once it is deleted.
     *
     * For screens that outlive an edit: reading once would leave them showing what was true when
     * they opened.
     */
    fun observeDocument(uuid: String): Flow<Document?>

    /**
     * A document of the library with exactly this content, or `null`. The oldest, when there are
     * several: importing a duplicate is allowed. Documents in the bin and documents of other apps
     * do not count.
     *
     * @param contentHash SHA-256 of the file, in hexadecimal.
     */
    suspend fun findInLibrary(contentHash: String): Document.Managed?

    /**
     * Adds a scan to the catalogue, with everything a document cannot be without: its activity and
     * a page for each of its pages. All of it or none of it.
     *
     * The file is expected to already be whole at [NewScan.filePath]: a document is catalogued
     * after its file is stored, never before. It causes [observeDocuments] to emit again.
     *
     * @throws Exception if the catalogue already has a document with that uuid or that file. The
     *   one it has is left as it is.
     */
    suspend fun addScan(scan: NewScan)

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
     * Marks a document of the library as a favorite, or takes the mark away. Nothing changes for a
     * document of another app, or for one that is not there.
     */
    suspend fun setFavorite(uuid: String, favorite: Boolean)

    /**
     * Forgets the document with this [uuid], and everything the catalogue kept about it.
     *
     * Only the catalogue entry goes; removing the document itself is storage's job.
     *
     * By uuid, which is the one thing that identifies a document. Where it is stored does not: two
     * entries could point at one file, and deleting by location took both.
     *
     * @throws NoSuchElementException If the catalogue holds no document with this [uuid].
     */
    suspend fun deleteDocument(uuid: String)
}
