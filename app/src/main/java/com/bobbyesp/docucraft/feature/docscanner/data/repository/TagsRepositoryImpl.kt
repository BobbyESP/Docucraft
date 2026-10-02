/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.repository

import androidx.room.withTransaction
import com.bobbyesp.docucraft.feature.docscanner.data.db.DocumentsDatabase
import com.bobbyesp.docucraft.feature.docscanner.data.db.dao.TagDao
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.DocumentTagEntity
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.TagEntity
import com.bobbyesp.docucraft.feature.docscanner.data.mapper.toManaged
import com.bobbyesp.docucraft.feature.docscanner.data.storage.DocumentLocations
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Tag
import com.bobbyesp.docucraft.feature.docscanner.domain.model.normalizedNameOf
import com.bobbyesp.docucraft.feature.docscanner.domain.model.tidyNameOf
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.TagChange
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.TagsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** @param now The clock, in epoch milliseconds. A parameter so that a test can hold it still. */
class TagsRepositoryImpl(
    private val database: DocumentsDatabase,
    private val locations: DocumentLocations,
    private val now: () -> Long = System::currentTimeMillis,
) : TagsRepository {

    private val tagDao: TagDao = database.tagDao()

    override fun observeTags(): Flow<List<Tag>> =
        tagDao.observeAll().map { tags -> tags.map { it.toModel() } }

    override fun observeTagsOf(documentUuid: String): Flow<List<Tag>> =
        tagDao.observeOf(documentUuid).map { tags -> tags.map { it.toModel() } }

    override fun observeDocumentsWithAll(tagUuids: List<String>): Flow<List<Document.Managed>> {
        // No tags is no filter, and a filter is what this is: the whole library is asked for
        // through the catalogue, not through here.
        val wanted = tagUuids.distinct()
        if (wanted.isEmpty()) return flowOf(emptyList())

        return tagDao
            .observeDocumentsWithAll(wanted, tagCount = wanted.size)
            .map { entities -> entities.map { it.toManaged(locations) } }
            .flowOn(Dispatchers.Default)
    }

    override suspend fun getOrCreate(uuid: String, name: String): Tag? = database.withTransaction {
        val tidy = tidyNameOf(name)
        if (tidy.isEmpty()) return@withTransaction null

        val normalized = normalizedNameOf(tidy)
        tagDao.byNormalizedName(normalized)?.let {
            return@withTransaction it.toModel()
        }

        val tag =
            TagEntity(
                uuid = uuid,
                name = tidy,
                normalizedName = normalized,
                color = null,
                homePosition = null,
                createdAt = now(),
            )
        tagDao.insert(tag)
        tag.toModel()
    }

    override suspend fun rename(uuid: String, name: String): TagChange = database.withTransaction {
        val tidy = tidyNameOf(name)
        if (tidy.isEmpty()) return@withTransaction TagChange.NameEmpty
        val tag = tagDao.byUuid(uuid) ?: return@withTransaction TagChange.NotFound

        val normalized = normalizedNameOf(tidy)
        val holder = tagDao.byNormalizedName(normalized)
        if (holder != null && holder.id != tag.id) return@withTransaction TagChange.NameTaken

        tagDao.update(tag.copy(name = tidy, normalizedName = normalized))
        TagChange.Done
    }

    override suspend fun setColor(uuid: String, color: String?) {
        database.withTransaction {
            val tag = tagDao.byUuid(uuid) ?: return@withTransaction
            tagDao.update(tag.copy(color = color))
        }
    }

    override suspend fun delete(uuid: String) {
        tagDao.delete(uuid)
    }

    override suspend fun tag(documentUuid: String, tagUuid: String) {
        database.withTransaction {
            // A document that is not the app's own is left alone: it is not organized.
            val documentId = tagDao.managedDocumentId(documentUuid) ?: return@withTransaction
            val tagId = tagDao.byUuid(tagUuid)?.id ?: return@withTransaction
            tagDao.insert(DocumentTagEntity(documentId, tagId, taggedAt = now()))
        }
    }

    override suspend fun untag(documentUuid: String, tagUuid: String) {
        tagDao.untag(documentUuid, tagUuid)
    }

    override suspend fun setHomeSections(tagUuids: List<String>) {
        database.withTransaction {
            tagDao.clearHomeSections()
            tagUuids.distinct().forEachIndexed { position, uuid ->
                tagDao.setHomePosition(uuid, position)
            }
        }
    }

    private fun TagEntity.toModel(): Tag =
        Tag(uuid = uuid, name = name, color = color, homePosition = homePosition)
}
