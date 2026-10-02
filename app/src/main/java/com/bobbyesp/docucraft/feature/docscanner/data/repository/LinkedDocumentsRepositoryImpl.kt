/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.repository

import com.bobbyesp.docucraft.feature.docscanner.data.db.dao.DocumentDao
import com.bobbyesp.docucraft.feature.docscanner.data.mapper.toEntity
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.LinkRegistration
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.LinkedDocumentFacts
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.LinkedDocumentsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.NewLinkedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.storage.StoredDocument
import com.bobbyesp.scanner.ContentRef
import java.util.UUID

/**
 * @param now The clock, in epoch milliseconds. A parameter so that a test can hold it still.
 * @param newUuid Names a document that is new to the catalogue.
 */
class LinkedDocumentsRepositoryImpl(
    private val documentDao: DocumentDao,
    private val now: () -> Long = System::currentTimeMillis,
    private val newUuid: () -> String = { UUID.randomUUID().toString() },
) : LinkedDocumentsRepository {

    override suspend fun register(link: NewLinkedDocument, limit: Int): LinkRegistration {
        val registered =
            documentDao.registerLinked(
                candidate = link.toEntity(uuid = newUuid(), createdAt = now()),
                keep = limit,
            )
        return LinkRegistration(
            uuid = registered.uuid,
            forgotten = registered.forgotten.map { ContentRef(it.uri) },
        )
    }

    override suspend fun forget(uuid: String): ContentRef? {
        val uri = documentDao.forgetLinked(uuid) ?: return null
        return ContentRef(uri)
    }

    override suspend fun keepInLibrary(uuid: String, stored: StoredDocument): Boolean =
        documentDao.keepLinkedInLibrary(
            uuid = uuid,
            filePath = stored.filePath,
            sizeBytes = stored.sizeBytes,
            contentHash = stored.contentHash,
            pageCount = stored.pageCount,
            at = now(),
        )

    override suspend fun describe(uuid: String, facts: LinkedDocumentFacts) {
        val known = documentDao.getByUuid(uuid) ?: return

        // What could not be learnt this time is left as it was known.
        val sizeBytes = facts.sizeBytes ?: known.sizeBytes
        val contentHash = facts.contentHash ?: known.contentHash
        val pageCount = facts.pageCount ?: known.pageCount

        // Every write to this table is a change every list of the library hears about, and this
        // is asked on each opening of a file that almost never changed.
        val unchanged =
            sizeBytes == known.sizeBytes &&
                contentHash == known.contentHash &&
                pageCount == known.pageCount &&
                facts.isProtected == known.isEncrypted
        if (unchanged) return

        documentDao.describeLinked(
            uuid = uuid,
            sizeBytes = sizeBytes,
            contentHash = contentHash,
            pageCount = pageCount,
            isEncrypted = facts.isProtected,
            // A different hash is different content, whatever the provider says about dates.
            contentChanged = contentHash != known.contentHash,
            at = now(),
        )
    }
}
