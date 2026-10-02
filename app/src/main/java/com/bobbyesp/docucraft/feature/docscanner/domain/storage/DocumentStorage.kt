/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.storage

import com.bobbyesp.scanner.ContentRef

/**
 * A document that now lives in storage the app controls, and what could be learnt of its file.
 *
 * @property filePath Where it is, relative to the app's files directory. This is what the catalogue
 *   keeps: it does not depend on how the file is later handed to the viewer or to another app.
 * @property contentHash SHA-256 of its bytes, in hexadecimal.
 * @property pageCount Its pages, or `null` when the file cannot be read as a document.
 */
data class StoredDocument(
    val filePath: String,
    val sizeBytes: Long,
    val contentHash: String,
    val pageCount: Int?,
)

/**
 * Where the documents the app keeps are stored.
 *
 * Scanners hand back locations in their own short-lived storage, so a document has to be copied
 * somewhere durable before it is any use. Which directory that is and how the file is named are
 * storage concerns, and neither belongs in the rules about what saving a scan means.
 */
interface DocumentStorage {

    /**
     * Copies the document at [source] into storage as the file of the document [documentUuid].
     *
     * The file is named after the document's uuid, not after anything the user can change or two
     * documents can share: renaming a document never touches it, and one document's file can never
     * be written over another's.
     *
     * It is there whole or not at all. Returning means the file is complete, is not empty and has
     * its final name; throwing means nothing was left behind.
     *
     * @throws com.bobbyesp.docucraft.feature.docscanner.domain.exception.ScanSaveException if the
     *   document could not be read or written, or came out empty.
     */
    suspend fun storeDocument(source: ContentRef, documentUuid: String): StoredDocument

    /**
     * Removes the file at [filePath], as [StoredDocument.filePath] gives it. Does nothing if it is
     * already gone, since the caller's intent — that it not be there — is satisfied either way.
     */
    suspend fun delete(filePath: String)
}
