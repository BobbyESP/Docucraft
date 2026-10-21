/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.storage

import com.bobbyesp.scanner.ContentRef

/**
 * A file read from start to end.
 *
 * @property contentHash SHA-256 of its bytes, in hexadecimal.
 */
data class MeasuredFile(val sizeBytes: Long, val contentHash: String)

/**
 * Reaching the files of other apps. They lend a file with the intent that opens it, and the loan
 * ends when they say: whether it can be made to last, and giving it back, are the platform's
 * business.
 */
interface ExternalDocumentAccess {

    /**
     * Tries to keep the permission to read [document] after the app that lent it is gone.
     *
     * @return Whether it is kept. Most apps do not allow it, and a shared file never does: the
     *   document then only opens for as long as the loan lasts.
     */
    fun keep(document: ContentRef): Boolean

    /** Gives back a permission that was kept. Nothing if none was. */
    fun release(document: ContentRef)

    /** Reads [document] once, to its end. `null` when it cannot be read. */
    suspend fun measure(document: ContentRef): MeasuredFile?
}
