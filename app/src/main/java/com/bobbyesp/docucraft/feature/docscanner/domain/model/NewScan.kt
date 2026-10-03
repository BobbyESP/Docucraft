/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.model

/**
 * A scan whose file is already stored, about to enter the catalogue.
 *
 * The write-side counterpart of [Document.Managed]. It carries what only the save knows: the
 * identity the document was given before its file was written, since the file is named after it,
 * and where that file went. The title and the description are missing on purpose: the user writes
 * those, a scan never does.
 *
 * @property originalName Name without extension, e.g. `Scan_20260919_142530`.
 * @property filePath Where the file is, relative to the app's files directory.
 * @property contentHash SHA-256 of the file, in hexadecimal.
 * @property pageCount Pages in the document: at least one.
 * @property capturedAtEpochMillis When it was scanned.
 * @property recognizeText Whether the text of its pages is to be recognized: a scan is images.
 */
data class NewScan(
    val uuid: String,
    val originalName: String,
    val filePath: String,
    val sizeBytes: Long,
    val contentHash: String,
    val pageCount: Int,
    val capturedAtEpochMillis: Long,
    val recognizeText: Boolean = false,
) {
    init {
        require(pageCount >= 1) { "A document has at least one page, not $pageCount" }
    }
}
