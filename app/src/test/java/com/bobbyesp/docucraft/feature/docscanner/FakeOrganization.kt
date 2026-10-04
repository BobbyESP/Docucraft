/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner

import com.bobbyesp.docucraft.feature.docscanner.domain.SortOption
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Folder
import com.bobbyesp.docucraft.feature.docscanner.domain.model.FolderTree
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Tag
import com.bobbyesp.docucraft.feature.docscanner.domain.model.normalizedNameOf
import com.bobbyesp.docucraft.feature.docscanner.domain.model.tidyNameOf
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.FolderChange
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.FoldersRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.TagChange
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.TagsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

fun testFolder(
    uuid: String,
    name: String = uuid,
    parentUuid: String? = null,
    pinnedAtEpochMillis: Long? = null,
    sort: SortOption? = null,
) =
    Folder(
        uuid = uuid,
        name = name,
        parentUuid = parentUuid,
        color = null,
        icon = null,
        pinnedAtEpochMillis = pinnedAtEpochMillis,
        sort = sort,
        createdAtEpochMillis = 0L,
    )

/**
 * The folders held in memory, with the rules the real ones keep: names unique among siblings, and
 * no folder inside itself. Which folder a document is in is kept by the document's uuid.
 */
class FakeFoldersRepository(
    folders: List<Folder> = emptyList(),
    documents: List<Document.Managed> = emptyList(),
    documentFolders: Map<String, String> = emptyMap(),
) : FoldersRepository {

    val folders = MutableStateFlow(folders)
    val documents = MutableStateFlow(documents)

    /** Document uuid to folder uuid. A document that is not here is in the root. */
    val documentFolders = MutableStateFlow(documentFolders)

    private var clock = 1L

    override fun observeFolders(parentUuid: String?): Flow<List<Folder>> = folders.map { all ->
        all.filter { it.parentUuid == parentUuid }.sortedBy { normalizedNameOf(it.name) }
    }

    override fun observePinned(): Flow<List<Folder>> = folders.map { all ->
        all.filter { it.isPinned }.sortedBy { it.pinnedAtEpochMillis }
    }

    override suspend fun getFolder(uuid: String): Folder? =
        folders.value.firstOrNull { it.uuid == uuid }

    override fun observeFolder(uuid: String): Flow<Folder?> = folders.map { all ->
        all.firstOrNull { it.uuid == uuid }
    }

    override fun observeFolderOf(documentUuid: String): Flow<Folder?> =
        combine(folders, documentFolders) { all, placed ->
            all.firstOrNull { it.uuid == placed[documentUuid] }
        }

    override suspend fun pathTo(uuid: String): List<Folder> {
        val path = ArrayDeque<Folder>()
        var folder = getFolder(uuid)
        while (folder != null) {
            path.addFirst(folder)
            folder = folder.parentUuid?.let { getFolder(it) }
        }
        return path
    }

    override fun observeDocuments(folderUuid: String?): Flow<List<Document.Managed>> =
        combine(documents, documentFolders) { all, placed ->
            all.filter { placed[it.uuid] == folderUuid }
        }

    override suspend fun create(uuid: String, name: String, parentUuid: String?): FolderChange {
        val tidy = tidyNameOf(name)
        if (tidy.isEmpty()) return FolderChange.NameEmpty
        if (parentUuid != null && getFolder(parentUuid) == null) return FolderChange.NotFound
        if (isNameTaken(tidy, parentUuid, except = null)) return FolderChange.NameTaken
        folders.update { it + testFolder(uuid = uuid, name = tidy, parentUuid = parentUuid) }
        return FolderChange.Done
    }

    override suspend fun rename(uuid: String, name: String): FolderChange {
        val tidy = tidyNameOf(name)
        if (tidy.isEmpty()) return FolderChange.NameEmpty
        val folder = getFolder(uuid) ?: return FolderChange.NotFound
        if (isNameTaken(tidy, folder.parentUuid, except = uuid)) return FolderChange.NameTaken
        change(uuid) { it.copy(name = tidy) }
        return FolderChange.Done
    }

    override suspend fun move(uuid: String, parentUuid: String?): FolderChange {
        val folder = getFolder(uuid) ?: return FolderChange.NotFound
        val pathToParent = parentUuid?.let { pathTo(it) }.orEmpty()
        if (parentUuid != null && pathToParent.isEmpty()) return FolderChange.NotFound
        if (FolderTree.wouldContainItself(uuid, pathToParent.map { it.uuid })) {
            return FolderChange.WouldContainItself
        }
        if (isNameTaken(folder.name, parentUuid, except = uuid)) return FolderChange.NameTaken
        change(uuid) { it.copy(parentUuid = parentUuid) }
        return FolderChange.Done
    }

    override suspend fun setAppearance(uuid: String, color: String?, icon: String?) {
        change(uuid) { it.copy(color = color, icon = icon) }
    }

    override suspend fun setPinned(uuid: String, pinned: Boolean) {
        change(uuid) { folder ->
            when {
                !pinned -> folder.copy(pinnedAtEpochMillis = null)
                folder.pinnedAtEpochMillis == null -> folder.copy(pinnedAtEpochMillis = clock++)
                else -> folder
            }
        }
    }

    override suspend fun setSort(uuid: String, sort: SortOption?) {
        change(uuid) { it.copy(sort = sort) }
    }

    override suspend fun delete(uuid: String) {
        val folder = getFolder(uuid) ?: return
        folders.update { all ->
            all.filterNot { it.uuid == uuid }
                .map { if (it.parentUuid == uuid) it.copy(parentUuid = folder.parentUuid) else it }
        }
        documentFolders.update { placed ->
            placed
                .mapNotNull { (document, where) ->
                    when {
                        where != uuid -> document to where
                        folder.parentUuid != null -> document to folder.parentUuid
                        else -> null
                    }
                }
                .toMap()
        }
    }

    override suspend fun moveDocuments(
        documentUuids: List<String>,
        folderUuid: String?,
    ): FolderChange {
        if (folderUuid != null && getFolder(folderUuid) == null) return FolderChange.NotFound
        documentFolders.update { placed ->
            if (folderUuid == null) placed - documentUuids.toSet()
            else placed + documentUuids.associateWith { folderUuid }
        }
        return FolderChange.Done
    }

    private fun isNameTaken(name: String, parentUuid: String?, except: String?): Boolean =
        folders.value.any {
            it.parentUuid == parentUuid &&
                it.uuid != except &&
                normalizedNameOf(it.name) == normalizedNameOf(name)
        }

    private fun change(uuid: String, change: (Folder) -> Folder) {
        folders.update { all -> all.map { if (it.uuid == uuid) change(it) else it } }
    }
}

/** The tags held in memory, and which documents carry them. */
class FakeTagsRepository(
    tags: List<Tag> = emptyList(),
    documents: List<Document.Managed> = emptyList(),
    assignments: Map<String, Set<String>> = emptyMap(),
) : TagsRepository {

    val tags = MutableStateFlow(tags)
    val documents = MutableStateFlow(documents)

    /** Document uuid to the uuids of its tags. */
    val assignments = MutableStateFlow(assignments)

    override fun observeTags(): Flow<List<Tag>> = tags.map { all ->
        all.sortedBy { normalizedNameOf(it.name) }
    }

    override fun observeTagsOf(documentUuid: String): Flow<List<Tag>> =
        combine(tags, assignments) { all, assigned ->
            all.filter { it.uuid in assigned[documentUuid].orEmpty() }
        }

    override fun observeDocumentsWithAll(tagUuids: List<String>): Flow<List<Document.Managed>> =
        combine(documents, assignments) { all, assigned ->
            if (tagUuids.isEmpty()) emptyList()
            else all.filter { assigned[it.uuid].orEmpty().containsAll(tagUuids) }
        }

    override suspend fun getOrCreate(uuid: String, name: String): Tag? {
        val tidy = tidyNameOf(name)
        if (tidy.isEmpty()) return null
        tags.value
            .firstOrNull { normalizedNameOf(it.name) == normalizedNameOf(tidy) }
            ?.let {
                return it
            }
        val tag = Tag(uuid = uuid, name = tidy, color = null, homePosition = null)
        tags.update { it + tag }
        return tag
    }

    override suspend fun rename(uuid: String, name: String): TagChange {
        val tidy = tidyNameOf(name)
        if (tidy.isEmpty()) return TagChange.NameEmpty
        if (tags.value.none { it.uuid == uuid }) return TagChange.NotFound
        val taken =
            tags.value.any {
                it.uuid != uuid && normalizedNameOf(it.name) == normalizedNameOf(tidy)
            }
        if (taken) return TagChange.NameTaken
        change(uuid) { it.copy(name = tidy) }
        return TagChange.Done
    }

    override suspend fun setColor(uuid: String, color: String?) {
        change(uuid) { it.copy(color = color) }
    }

    override suspend fun delete(uuid: String) {
        tags.update { all -> all.filterNot { it.uuid == uuid } }
        assignments.update { assigned -> assigned.mapValues { (_, tags) -> tags - uuid } }
    }

    override suspend fun tag(documentUuid: String, tagUuid: String) {
        assignments.update { it + (documentUuid to it[documentUuid].orEmpty() + tagUuid) }
    }

    override suspend fun untag(documentUuid: String, tagUuid: String) {
        assignments.update { it + (documentUuid to it[documentUuid].orEmpty() - tagUuid) }
    }

    override suspend fun setHomeSections(tagUuids: List<String>) {
        tags.update { all ->
            all.map { it.copy(homePosition = tagUuids.indexOf(it.uuid).takeIf { at -> at >= 0 }) }
        }
    }

    private fun change(uuid: String, change: (Tag) -> Tag) {
        tags.update { all -> all.map { if (it.uuid == uuid) change(it) else it } }
    }
}
