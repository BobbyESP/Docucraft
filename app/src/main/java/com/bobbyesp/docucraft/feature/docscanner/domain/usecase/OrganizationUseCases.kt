/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Folder
import com.bobbyesp.docucraft.feature.docscanner.domain.model.FolderIcon
import com.bobbyesp.docucraft.feature.docscanner.domain.model.LabelColor
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Tag
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.DocumentsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.FolderChange
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.FoldersRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.TagChange
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.TagsRepository
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * How deep folders go. The catalogue sets no limit; this is where the app does, so that a path
 * still fits on a phone and a document is never more than a few taps away.
 */
object FolderDepth {
    /** A folder in the root is at depth 1. */
    const val MAX = 4

    /**
     * Whether a folder can be put inside the one at the end of [pathToParent], which is empty for
     * the root.
     */
    fun allowsFolderIn(pathToParent: List<Folder>): Boolean = pathToParent.size < MAX
}

/** The library's documents, or those of them that carry every one of [tagUuids]. */
class ObserveLibraryUseCase(
    private val documents: DocumentsRepository,
    private val tags: TagsRepository,
) {
    operator fun invoke(tagUuids: Set<String> = emptySet()): Flow<List<Document.Managed>> =
        if (tagUuids.isEmpty()) documents.observeDocuments()
        else tags.observeDocumentsWithAll(tagUuids.toList())
}

/** A tag the user gave a section of its own in Home, with the documents that carry it. */
data class TagSection(val tag: Tag, val documents: List<Document.Managed>)

/**
 * What Home shows between Recents and the whole library: the pinned folders, and a section for each
 * tag the user chose, in the order they chose.
 */
data class HomeSections(
    val pinnedFolders: List<Folder> = emptyList(),
    val tagSections: List<TagSection> = emptyList(),
)

class ObserveHomeSectionsUseCase(
    private val folders: FoldersRepository,
    private val tags: TagsRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<HomeSections> {
        val sections =
            tags.observeTags().flatMapLatest { all ->
                val chosen = all.filter { it.homePosition != null }.sortedBy { it.homePosition }
                if (chosen.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(
                        chosen.map { tag ->
                            tags.observeDocumentsWithAll(listOf(tag.uuid))
                        }
                    ) { documents ->
                        chosen.mapIndexed { index, tag -> TagSection(tag, documents[index]) }
                    }
                }
            }
        return combine(folders.observePinned(), sections, ::HomeSections)
    }
}

class SetDocumentFavoriteUseCase(private val documents: DocumentsRepository) {
    suspend operator fun invoke(documentUuid: String, favorite: Boolean) =
        documents.setFavorite(documentUuid, favorite)
}

/**
 * Creates a folder as the user described it, or changes one: its name, and how it looks.
 *
 * @param newUuid Where the identity of a new folder comes from. A parameter so a test can name it.
 */
class SaveFolderUseCase(
    private val folders: FoldersRepository,
    private val newUuid: () -> String = { UUID.randomUUID().toString() },
) {
    /**
     * @param folderUuid The folder to change, or `null` to create one in [parentUuid].
     * @return [FolderChange.NotFound] also when the folder would be deeper than [FolderDepth.MAX].
     */
    suspend operator fun invoke(
        folderUuid: String?,
        parentUuid: String?,
        name: String,
        color: LabelColor?,
        icon: FolderIcon,
    ): FolderChange {
        val uuid = folderUuid ?: newUuid()
        val change =
            if (folderUuid == null) {
                val pathToParent = parentUuid?.let { folders.pathTo(it) }.orEmpty()
                if (!FolderDepth.allowsFolderIn(pathToParent)) return FolderChange.NotFound
                folders.create(uuid, name, parentUuid)
            } else {
                folders.rename(uuid, name)
            }
        if (change == FolderChange.Done) {
            // The default icon is kept as none, so that it follows the default if that changes.
            folders.setAppearance(
                uuid = uuid,
                color = color?.key,
                icon = icon.takeIf { it != FolderIcon.Default }?.key,
            )
        }
        return change
    }
}

/**
 * Creates a tag, or changes one: its name and its color.
 *
 * @param newUuid Where the identity of a new tag comes from.
 */
class SaveTagUseCase(
    private val tags: TagsRepository,
    private val newUuid: () -> String = { UUID.randomUUID().toString() },
) {
    /**
     * @param tagUuid The tag to change, or `null` to create one. Creating a tag whose name another
     *   one has is [TagChange.NameTaken]: the user asked for a new tag and would get an old one.
     */
    suspend operator fun invoke(tagUuid: String?, name: String, color: LabelColor?): TagChange {
        val uuid =
            if (tagUuid == null) {
                val created = newUuid()
                val tag = tags.getOrCreate(created, name) ?: return TagChange.NameEmpty
                if (tag.uuid != created) return TagChange.NameTaken
                created
            } else {
                val change = tags.rename(tagUuid, name)
                if (change != TagChange.Done) return change
                tagUuid
            }
        tags.setColor(uuid, color?.key)
        return TagChange.Done
    }
}

/**
 * Puts on a document the tag called [name], creating it when there is none: what typing a name in
 * the document's tags and confirming it means.
 */
class TagDocumentByNameUseCase(
    private val tags: TagsRepository,
    private val newUuid: () -> String = { UUID.randomUUID().toString() },
) {
    /** @return The tag, or `null` when [name] is empty. */
    suspend operator fun invoke(documentUuid: String, name: String): Tag? {
        val tag = tags.getOrCreate(newUuid(), name) ?: return null
        tags.tag(documentUuid, tag.uuid)
        return tag
    }
}

/**
 * Which tags have a section in Home, and where: gives one to a tag at the end, takes it away, or
 * moves it one place.
 *
 * Each change starts from the sections as they are when it runs, one change at a time: two taps in
 * a row would otherwise both start from what the screen showed before the first, and the second
 * would undo it.
 */
class ArrangeHomeSectionsUseCase(private val tags: TagsRepository) {

    private val oneAtATime = Mutex()

    suspend fun setShown(tagUuid: String, shown: Boolean) = oneAtATime.withLock {
        val current = sections()
        tags.setHomeSections(if (shown) (current - tagUuid) + tagUuid else current - tagUuid)
    }

    /** @param by How many places: negative towards the top of Home. */
    suspend fun move(tagUuid: String, by: Int) = oneAtATime.withLock {
        val current = sections().toMutableList()
        val from = current.indexOf(tagUuid)
        if (from < 0) return@withLock
        val to = (from + by).coerceIn(0, current.lastIndex)
        if (to == from) return@withLock
        current.add(to, current.removeAt(from))
        tags.setHomeSections(current)
    }

    private suspend fun sections(): List<String> =
        tags
            .observeTags()
            .first()
            .filter { it.homePosition != null }
            .sortedBy { it.homePosition }
            .map { it.uuid }
}
