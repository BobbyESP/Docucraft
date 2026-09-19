/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.repository

import android.net.Uri
import com.bobbyesp.docucraft.feature.docscanner.data.db.dao.ScannedDocumentDao
import com.bobbyesp.docucraft.feature.docscanner.data.mapper.toEntity
import com.bobbyesp.docucraft.feature.docscanner.data.mapper.toModel
import com.bobbyesp.docucraft.feature.docscanner.domain.model.NewScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.LocalDocumentsRepository
import java.text.Normalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class LocalDocumentsRepositoryImpl(private val scannedDocumentDao: ScannedDocumentDao) :
    LocalDocumentsRepository {

    override suspend fun observeDocuments(): Flow<List<ScannedDocument>> =
        scannedDocumentDao
            .observeDocuments()
            .map { entities -> entities.map { it.toModel() } }
            .flowOn(Dispatchers.Default)

    override suspend fun searchDocuments(query: String): List<ScannedDocument> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val ftsQuery = buildFtsQuery(trimmed)

        val result = scannedDocumentDao.searchDocumentsFts(ftsQuery)

        return result.map { it.toModel() }
    }

    override suspend fun getDocument(uuid: String): ScannedDocument {
        require(uuid.isNotEmpty()) { "Document UUID must not be empty" }
        val entity =
            scannedDocumentDao.getByUuid(uuid)
                ?: throw NoSuchElementException("No document found with ID: $uuid")
        return entity.toModel()
    }

    override suspend fun saveDocument(document: NewScannedDocument) {
        scannedDocumentDao.insert(document.toEntity())
    }

    override suspend fun modifyFields(uuid: String, title: String?, description: String?) {
        require(uuid.isNotEmpty()) { "Document UUID must not be empty" }

        val existing =
            scannedDocumentDao.getByUuid(uuid)
                ?: throw NoSuchElementException("No document found with UUID: $uuid")

        val updated = existing.copy(title = title, description = description)

        scannedDocumentDao.update(updated)
    }

    override suspend fun deleteDocument(path: Uri) {
        // First remove from database to maintain referential integrity
        val deletedCount = scannedDocumentDao.deleteByPath(path.toString())

        if (deletedCount <= 0) {
            throw IllegalArgumentException("No document found with path: $path")
        }
    }

    private fun normalize(text: String): String {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .lowercase()
    }

    private fun buildFtsQuery(query: String): String {
        return query
            .trim()
            .split("\\s+".toRegex())
            .map { normalize(it) }
            .joinToString(" AND ") { "$it*" }
    }
}
