/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.repository

import androidx.room.withTransaction
import com.bobbyesp.docucraft.feature.docscanner.data.db.DocumentsDatabase
import com.bobbyesp.docucraft.feature.docscanner.data.db.dao.FolderDao
import com.bobbyesp.docucraft.feature.docscanner.data.db.dao.FolderRow
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.FolderEntity
import com.bobbyesp.docucraft.feature.docscanner.data.mapper.toManaged
import com.bobbyesp.docucraft.feature.docscanner.data.storage.DocumentLocations
import com.bobbyesp.docucraft.feature.docscanner.domain.SortOption
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Folder
import com.bobbyesp.docucraft.feature.docscanner.domain.model.FolderTree
import com.bobbyesp.docucraft.feature.docscanner.domain.model.normalizedNameOf
import com.bobbyesp.docucraft.feature.docscanner.domain.model.tidyNameOf
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.FolderChange
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.FoldersRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Every change runs in a transaction, where a rule is checked and the change it allows is made
 * together. The triggers and the indices would stop a wrong write as well, but with an exception;
 * checking first is what lets a name that is taken come back as an answer.
 *
 * @param now The clock, in epoch milliseconds. A parameter so that a test can hold it still.
 */
class FoldersRepositoryImpl(
    private val database: DocumentsDatabase,
    private val locations: DocumentLocations,
    private val now: () -> Long = System::currentTimeMillis,
) : FoldersRepository {

    private val folderDao: FolderDao = database.folderDao()

    override fun observeFolders(parentUuid: String?): Flow<List<Folder>> {
        val rows =
            if (parentUuid == null) folderDao.observeRoot()
            else folderDao.observeChildren(parentUuid)
        return rows.map { folders -> folders.map { it.toModel() } }
    }

    override fun observePinned(): Flow<List<Folder>> =
        folderDao.observePinned().map { folders -> folders.map { it.toModel() } }

    override suspend fun getFolder(uuid: String): Folder? = folderDao.rowByUuid(uuid)?.toModel()

    override suspend fun pathTo(uuid: String): List<Folder> = database.withTransaction {
        pathRowsTo(uuid).map { it.toModel() }
    }

    override fun observeDocuments(folderUuid: String?): Flow<List<Document.Managed>> {
        val documents =
            if (folderUuid == null) folderDao.observeDocumentsInRoot()
            else folderDao.observeDocumentsIn(folderUuid)
        return documents
            .map { entities -> entities.map { it.toManaged(locations) } }
            .flowOn(Dispatchers.Default)
    }

    override suspend fun create(uuid: String, name: String, parentUuid: String?): FolderChange =
        database.withTransaction {
            val tidy = tidyNameOf(name)
            if (tidy.isEmpty()) return@withTransaction FolderChange.NameEmpty

            val parentId =
                if (parentUuid == null) null
                else
                    folderDao.byUuid(parentUuid)?.id ?: return@withTransaction FolderChange.NotFound

            val normalized = normalizedNameOf(tidy)
            if (folderDao.isNameTaken(parentId, normalized, exceptId = NO_FOLDER)) {
                return@withTransaction FolderChange.NameTaken
            }

            val createdAt = now()
            folderDao.insert(
                FolderEntity(
                    uuid = uuid,
                    name = tidy,
                    normalizedName = normalized,
                    parentId = parentId,
                    color = null,
                    icon = null,
                    pinnedAt = null,
                    sortCriteria = null,
                    sortOrder = null,
                    createdAt = createdAt,
                    updatedAt = createdAt,
                )
            )
            FolderChange.Done
        }

    override suspend fun rename(uuid: String, name: String): FolderChange =
        database.withTransaction {
            val tidy = tidyNameOf(name)
            if (tidy.isEmpty()) return@withTransaction FolderChange.NameEmpty
            val folder = folderDao.byUuid(uuid) ?: return@withTransaction FolderChange.NotFound

            val normalized = normalizedNameOf(tidy)
            if (folderDao.isNameTaken(folder.parentId, normalized, exceptId = folder.id)) {
                return@withTransaction FolderChange.NameTaken
            }

            folderDao.update(
                folder.copy(name = tidy, normalizedName = normalized, updatedAt = now())
            )
            FolderChange.Done
        }

    override suspend fun move(uuid: String, parentUuid: String?): FolderChange =
        database.withTransaction {
            val folder = folderDao.byUuid(uuid) ?: return@withTransaction FolderChange.NotFound

            val pathToParent = if (parentUuid == null) emptyList() else pathRowsTo(parentUuid)
            if (parentUuid != null && pathToParent.isEmpty()) {
                return@withTransaction FolderChange.NotFound
            }
            // The triggers only stop a folder from being its own parent. A longer loop is stopped
            // here: SQLite 3.9 has no way to walk the ancestors from inside a trigger.
            if (FolderTree.wouldContainItself(uuid, pathToParent.map { it.folder.uuid })) {
                return@withTransaction FolderChange.WouldContainItself
            }

            val parentId = pathToParent.lastOrNull()?.folder?.id
            if (folderDao.isNameTaken(parentId, folder.normalizedName, exceptId = folder.id)) {
                return@withTransaction FolderChange.NameTaken
            }

            folderDao.update(folder.copy(parentId = parentId, updatedAt = now()))
            FolderChange.Done
        }

    override suspend fun setAppearance(uuid: String, color: String?, icon: String?) {
        change(uuid) { it.copy(color = color, icon = icon) }
    }

    override suspend fun setPinned(uuid: String, pinned: Boolean) {
        // Pinning what is already pinned keeps its place among the pinned folders.
        change(uuid) { folder ->
            when {
                !pinned -> folder.copy(pinnedAt = null)
                folder.pinnedAt == null -> folder.copy(pinnedAt = now())
                else -> folder
            }
        }
    }

    override suspend fun setSort(uuid: String, sort: SortOption?) {
        change(uuid) { it.copy(sortCriteria = sort?.criteria, sortOrder = sort?.order) }
    }

    override suspend fun delete(uuid: String) {
        database.withTransaction {
            val folder = folderDao.byUuid(uuid) ?: return@withTransaction

            // Its name is given up first: a subfolder with the same name is about to take its
            // place among the same siblings, and the two cannot hold the name at once.
            folderDao.update(folder.copy(normalizedName = "$BEING_DELETED${folder.uuid}"))

            val taken =
                folderDao
                    .childrenOf(folder.parentId)
                    .filter { it.id != folder.id }
                    .mapTo(HashSet()) { it.normalizedName }

            val movedAt = now()
            for (child in folderDao.childrenOf(folder.id)) {
                val name = FolderTree.freeName(child.name, taken)
                val normalized = normalizedNameOf(name)
                taken += normalized
                folderDao.update(
                    child.copy(
                        parentId = folder.parentId,
                        name = name,
                        normalizedName = normalized,
                        updatedAt = movedAt,
                    )
                )
            }

            folderDao.moveDocumentsOut(from = folder.id, to = folder.parentId)
            folderDao.delete(folder.id)
        }
    }

    override suspend fun moveDocuments(
        documentUuids: List<String>,
        folderUuid: String?,
    ): FolderChange = database.withTransaction {
        val folderId =
            if (folderUuid == null) null
            else folderDao.byUuid(folderUuid)?.id ?: return@withTransaction FolderChange.NotFound

        folderDao.moveDocuments(documentUuids, folderId, updatedAt = now())
        FolderChange.Done
    }

    private suspend fun change(uuid: String, change: (FolderEntity) -> FolderEntity) {
        database.withTransaction {
            val folder = folderDao.byUuid(uuid) ?: return@withTransaction
            val changed = change(folder)
            if (changed != folder) folderDao.update(changed.copy(updatedAt = now()))
        }
    }

    /** From the root down to the folder, by following parents up. Inside a transaction. */
    private suspend fun pathRowsTo(uuid: String): List<FolderRow> {
        val path = ArrayDeque<FolderRow>()
        val seen = HashSet<Long>()
        var row = folderDao.rowByUuid(uuid)
        // The rules keep a loop from existing. If one ever did, walking it would never end.
        while (row != null && seen.add(row.folder.id)) {
            path.addFirst(row)
            row = row.folder.parentId?.let { folderDao.rowById(it) }
        }
        return path
    }

    private fun FolderRow.toModel(): Folder =
        Folder(
            uuid = folder.uuid,
            name = folder.name,
            parentUuid = parentUuid,
            color = folder.color,
            icon = folder.icon,
            pinnedAtEpochMillis = folder.pinnedAt,
            sort =
                folder.sortCriteria?.let { criteria ->
                    SortOption(criteria, folder.sortOrder ?: SortOption.Order.DESC)
                },
            createdAtEpochMillis = folder.createdAt,
        )

    private companion object {
        /** An id no folder has, for "no folder is to be left out of the comparison". */
        const val NO_FOLDER = 0L

        /**
         * What a folder being deleted is called while its contents are moved out. It starts with a
         * line break, which no name has once it is normalized, so it collides with none.
         */
        const val BEING_DELETED = "\ndeleting:"
    }
}
