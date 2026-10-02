/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.repository

import com.bobbyesp.docucraft.feature.docscanner.data.db.dao.DocumentDao
import com.bobbyesp.docucraft.feature.docscanner.data.mapper.toEntity
import com.bobbyesp.docucraft.feature.docscanner.data.mapper.toManaged
import com.bobbyesp.docucraft.feature.docscanner.data.mapper.toModel
import com.bobbyesp.docucraft.feature.docscanner.data.storage.DocumentLocations
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.model.NewScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.DocumentsRepository
import java.text.Normalizer
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** @param now The clock, in epoch milliseconds. A parameter so that a test can hold it still. */
class DocumentsRepositoryImpl(
    private val documentDao: DocumentDao,
    private val locations: DocumentLocations,
    private val now: () -> Long = System::currentTimeMillis,
) : DocumentsRepository {

    override fun observeDocuments(): Flow<List<Document.Managed>> =
        documentDao
            .observeLibrary()
            .map { entities -> entities.map { it.toManaged(locations) } }
            .flowOn(Dispatchers.Default)

    override suspend fun searchDocuments(query: String): List<Document.Managed> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val ftsQuery = buildFtsQuery(trimmed)

        val result = documentDao.search(ftsQuery)

        return result.map { it.toManaged(locations) }
    }

    override fun observeDocument(uuid: String): Flow<Document?> =
        documentDao
            .observeByUuid(uuid)
            .map { entity -> entity?.toModel(locations) }
            .flowOn(Dispatchers.Default)

    override suspend fun getDocument(uuid: String): Document {
        require(uuid.isNotEmpty()) { "Document UUID must not be empty" }
        val entity =
            documentDao.getByUuid(uuid)
                ?: throw NoSuchElementException("No document found with ID: $uuid")
        return entity.toModel(locations)
    }

    override suspend fun saveDocument(document: NewScannedDocument) {
        documentDao.insertManaged(
            document.toEntity(uuid = UUID.randomUUID().toString(), createdAt = now())
        )
    }

    override suspend fun modifyFields(uuid: String, title: String?, description: String?) {
        require(uuid.isNotEmpty()) { "Document UUID must not be empty" }

        val updated = documentDao.updateFields(uuid, title, description, updatedAt = now())

        if (updated <= 0) throw NoSuchElementException("No document found with UUID: $uuid")
    }

    override suspend fun deleteDocument(uuid: String) {
        val deleted = documentDao.deleteByUuid(uuid)

        if (deleted <= 0) throw NoSuchElementException("No document found with UUID: $uuid")
    }

    private fun normalize(text: String): String {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .lowercase()
    }

    /**
     * Every term as a prefix, and all of them required. A space between terms is what requires them
     * all: Android's SQLite is compiled with the standard query syntax, where `AND` is not an
     * operator but one more word to look for.
     */
    private fun buildFtsQuery(query: String): String {
        return query
            .trim()
            .split("\\s+".toRegex())
            .map { normalize(it) }
            .joinToString(" ") { "$it*" }
    }
}
