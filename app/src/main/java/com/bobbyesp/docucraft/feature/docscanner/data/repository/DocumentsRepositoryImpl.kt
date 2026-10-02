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
import com.bobbyesp.docucraft.feature.docscanner.domain.model.NewScan
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.DocumentsRepository
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

    override suspend fun addScan(scan: NewScan) {
        documentDao.insertManaged(scan.toEntity(createdAt = now()))
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
}
