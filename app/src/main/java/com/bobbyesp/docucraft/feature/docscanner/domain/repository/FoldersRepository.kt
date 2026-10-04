/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.repository

import com.bobbyesp.docucraft.feature.docscanner.domain.SortOption
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Folder
import kotlinx.coroutines.flow.Flow

/**
 * How a change to a folder ended. The user can cause every one of these by what they type or where
 * they drop a folder, so none of them is an exception.
 */
sealed interface FolderChange {
    data object Done : FolderChange

    /** Another folder with the same parent already has that name, however it is capitalized. */
    data object NameTaken : FolderChange

    /** The name is empty once the spaces around it are gone. */
    data object NameEmpty : FolderChange

    /** The folder, or the folder it was to go into, is not there any more. */
    data object NotFound : FolderChange

    /** The folder was to go inside itself, or inside a folder that is inside it. */
    data object WouldContainItself : FolderChange
}

/**
 * The folders of the library, and which documents are in them.
 *
 * Every change is one transaction: a folder is never seen half moved, and a name is checked and
 * taken together.
 */
interface FoldersRepository {

    /** The folders directly inside [parentUuid], or in the root for `null`, by name. */
    fun observeFolders(parentUuid: String?): Flow<List<Folder>>

    /** The folders pinned to Home, in the order they were pinned. */
    fun observePinned(): Flow<List<Folder>>

    suspend fun getFolder(uuid: String): Folder?

    /** One folder, emitted again whenever it or what it holds changes, and `null` once deleted. */
    fun observeFolder(uuid: String): Flow<Folder?>

    /** The folder a document is in, or `null` while it is in the root. */
    fun observeFolderOf(documentUuid: String): Flow<Folder?>

    /**
     * From the root down to the folder [uuid], itself included. Empty when there is no such folder.
     */
    suspend fun pathTo(uuid: String): List<Folder>

    /**
     * The library's documents directly in [folderUuid], or in the root for `null`, newest first.
     * Never the bin's.
     */
    fun observeDocuments(folderUuid: String?): Flow<List<Document.Managed>>

    /** @param uuid The identity the new folder is given. */
    suspend fun create(uuid: String, name: String, parentUuid: String?): FolderChange

    suspend fun rename(uuid: String, name: String): FolderChange

    /** @param parentUuid Where it goes: a folder, or the root for `null`. */
    suspend fun move(uuid: String, parentUuid: String?): FolderChange

    /** @param color A key of the palette, or `null` for the default. The same goes for [icon]. */
    suspend fun setAppearance(uuid: String, color: String?, icon: String?)

    suspend fun setPinned(uuid: String, pinned: Boolean)

    suspend fun setSort(uuid: String, sort: SortOption?)

    /**
     * Deletes a folder and never a document: everything directly in it, documents in the bin and
     * subfolders included, goes to the folder it was in, or to the root. A subfolder that arrives
     * where its name is taken is renamed "Name (2)".
     */
    suspend fun delete(uuid: String)

    /**
     * Deletes a folder together with what it holds: the folders inside it, however deep, and their
     * documents, which go to the bin. Asked for by name, and never what [delete] does.
     *
     * No document is deleted here either. Those sent to the bin, and those that were already there,
     * are left in the folder this one was in, which is where they come back to if restored.
     */
    suspend fun deleteWithContents(uuid: String)

    /** @param folderUuid Where they go: a folder, or the root for `null`. */
    suspend fun moveDocuments(documentUuids: List<String>, folderUuid: String?): FolderChange
}
