/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner

import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.model.NewScan
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.DocumentsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * A catalogue held in memory, for tests of what sits above it. It keeps what it was asked to do,
 * and can be told to fail.
 */
class FakeDocumentsRepository(
    documents: List<Document.Managed> = emptyList(),
    linked: List<Document.Linked> = emptyList(),
) : DocumentsRepository {

    val documents = MutableStateFlow(documents)

    /** The documents of other apps: in the catalogue, and in none of the library's lists. */
    val linked = MutableStateFlow(linked)

    /** Every scan the catalogue was asked to add, in order. */
    val added = mutableListOf<NewScan>()

    /** The uuids it was asked to delete, in order. */
    val deleted = mutableListOf<String>()

    var addFailure: Exception? = null

    override fun observeDocuments(): Flow<List<Document.Managed>> = documents

    override suspend fun getDocument(uuid: String): Document =
        documents.value.firstOrNull { it.uuid == uuid }
            ?: linked.value.firstOrNull { it.uuid == uuid }
            ?: throw NoSuchElementException("No document found with ID: $uuid")

    override fun observeDocument(uuid: String): Flow<Document?> = documents.map { all ->
        all.firstOrNull { it.uuid == uuid }
    }

    /** The hash each document of the library was stored with. */
    val hashes = mutableMapOf<String, String>()

    override suspend fun findInLibrary(contentHash: String): Document.Managed? =
        documents.value.firstOrNull { hashes[it.uuid] == contentHash }

    override suspend fun addScan(scan: NewScan) {
        addFailure?.let { throw it }
        added += scan
    }

    override suspend fun modifyFields(uuid: String, title: String?, description: String?) {
        documents.update { all ->
            all.map {
                if (it.uuid == uuid) it.copy(title = title, description = description) else it
            }
        }
    }

    override suspend fun deleteDocument(uuid: String) {
        deleted += uuid
        documents.update { all -> all.filterNot { it.uuid == uuid } }
    }
}
