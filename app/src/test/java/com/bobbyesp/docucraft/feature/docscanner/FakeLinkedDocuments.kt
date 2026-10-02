/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner

import com.bobbyesp.docucraft.feature.docscanner.domain.repository.LinkRegistration
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.LinkedDocumentFacts
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.LinkedDocumentsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.NewLinkedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.storage.ExternalDocumentAccess
import com.bobbyesp.docucraft.feature.docscanner.domain.storage.MeasuredFile
import com.bobbyesp.docucraft.feature.docscanner.domain.storage.StoredDocument
import com.bobbyesp.scanner.ContentRef

/**
 * The documents of other apps, held in memory. A location is one document, as in the catalogue; the
 * limit is the catalogue's own business and is only obeyed when a test says what to forget.
 */
class FakeLinkedDocumentsRepository : LinkedDocumentsRepository {

    /** Every link asked for, in order, with the limit it was asked with. */
    val registered = mutableListOf<Pair<NewLinkedDocument, Int>>()

    /** The uuid given to each location. */
    val uuids = mutableMapOf<ContentRef, String>()

    /** What the next registration has to forget to make room. */
    var forgottenOnRegister: List<ContentRef> = emptyList()

    /** Thrown by [register] when set. */
    var registerFailure: Exception? = null

    /** The uuids it was asked to forget, in order. */
    val forgotten = mutableListOf<String>()

    /** What was noted about each document, in order. */
    val described = mutableListOf<Pair<String, LinkedDocumentFacts>>()

    override suspend fun register(link: NewLinkedDocument, limit: Int): LinkRegistration {
        registerFailure?.let { throw it }
        registered += link to limit
        val uuid = uuids.getOrPut(link.location) { "linked-${uuids.size + 1}" }
        return LinkRegistration(uuid, forgottenOnRegister)
    }

    override suspend fun forget(uuid: String): ContentRef? {
        forgotten += uuid
        val location = uuids.entries.firstOrNull { it.value == uuid }?.key ?: return null
        uuids.remove(location)
        return location
    }

    override suspend fun describe(uuid: String, facts: LinkedDocumentFacts) {
        described += uuid to facts
    }

    /** What was kept in the library, in order. */
    val kept = mutableListOf<Pair<String, StoredDocument>>()

    /** What [keepInLibrary] answers: `false` for a document that is no longer linked. */
    var keeps = true

    /** Thrown by [keepInLibrary] when set. */
    var keepFailure: Exception? = null

    override suspend fun keepInLibrary(uuid: String, stored: StoredDocument): Boolean {
        keepFailure?.let { throw it }
        if (keeps) kept += uuid to stored
        return keeps
    }
}

/** Other apps' files, as a test says they are. */
class FakeExternalDocumentAccess : ExternalDocumentAccess {

    /** The locations whose permission can be kept. */
    val keepable = mutableSetOf<ContentRef>()

    /** What was asked, in order: `keep`, `release` or `measure`, and of which location. */
    val calls = mutableListOf<Pair<String, ContentRef>>()

    /** What reading each location finds. A location that is not here cannot be read. */
    val files = mutableMapOf<ContentRef, MeasuredFile>()

    override fun keep(document: ContentRef): Boolean {
        calls += "keep" to document
        return document in keepable
    }

    override fun release(document: ContentRef) {
        calls += "release" to document
    }

    override suspend fun measure(document: ContentRef): MeasuredFile? {
        calls += "measure" to document
        return files[document]
    }
}
