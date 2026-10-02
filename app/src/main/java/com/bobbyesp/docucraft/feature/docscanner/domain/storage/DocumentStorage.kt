/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.storage

import com.bobbyesp.scanner.ContentRef

/** A document that now lives in storage the app controls. */
data class StoredDocument(val location: ContentRef, val sizeBytes: Long)

/**
 * Where scanned documents are kept.
 *
 * Scanners hand back locations in their own short-lived storage, so a document has to be copied
 * somewhere durable before it is any use. Which directory that is and how the file is addressed
 * afterwards are storage concerns, and neither belongs in the rules about what saving a scan means.
 */
interface DocumentStorage {

    /**
     * Copies the document at [source] into storage the app controls.
     *
     * @param filename Name without extension.
     * @return Where it now lives, and how big it turned out to be.
     * @throws Exception if the document could not be read or written.
     */
    suspend fun storeDocument(source: ContentRef, filename: String): StoredDocument

    /**
     * Removes something this storage put there. Does nothing if it is already gone, since the
     * caller's intent — that it not be there — is satisfied either way.
     */
    suspend fun delete(location: ContentRef)
}
