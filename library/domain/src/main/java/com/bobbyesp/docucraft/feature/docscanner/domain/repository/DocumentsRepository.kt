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
     * One document, emitted again whenever it changes, and `null` once it is deleted or sent to the
     * bin: for everything but the bin itself, a document that is there is one that is gone.
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
     * The bin: the documents that were deleted and can still be brought back, the last one deleted
     * first.
     */
    fun observeBin(): Flow<List<Document.Managed>>

    /**
     * Sends a document of the library to the bin. It leaves the library, search and Recents, and
     * keeps everything else: its folder, its tags, its text and its file.
     *
     * @return Whether it went: `false` for a document that is already there, or is not one the app
     *   keeps.
     */
    suspend fun moveToBin(uuid: String): Boolean

    /**
     * Brings a document back from the bin, to the folder it is in. That folder is one that still
     * exists: deleting a folder moves what it holds, the bin's documents too, to the folder above.
     *
     * @return Whether it came back: `false` for a document that is not in the bin.
     */
    suspend fun restoreFromBin(uuid: String): Boolean

    /** What has been in the bin since [epochMillis] or longer. */
    suspend fun binnedUntil(epochMillis: Long): List<Document.Managed>

    /**
     * Deletes a document of the bin for good, with everything the catalogue kept about it. Only the
     * catalogue entry goes; removing the file is storage's job, afterwards.
     *
     * A document that is not in the bin is left alone, whatever asked: nothing but the user naming
     * a document deletes one from the library.
     *
     * @return Whether it was deleted.
     */
    suspend fun deleteFromBin(uuid: String): Boolean

    /** Where the file of every document the app keeps is, by uuid. The bin's are included. */
    suspend fun keptFiles(): Map<String, String>

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
