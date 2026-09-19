/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.model

import com.bobbyesp.scanner.ContentRef

/**
 * A document that has been stored but not yet catalogued.
 *
 * The write-side counterpart of [ScannedDocument]: it has no id, because nothing has assigned one
 * yet, and it names no storage technology, because the catalogue is free to use whichever.
 *
 * @property filename Name without extension, e.g. `Scan_20260919_142530`.
 * @property location Where the stored document lives.
 * @property createdTimestamp When the document was captured, in epoch milliseconds.
 * @property fileSizeBytes Size of the stored document.
 * @property pageCount Pages in the document.
 * @property thumbnail Where its preview image lives, if one could be produced.
 */
data class NewScannedDocument(
    val filename: String,
    val location: ContentRef,
    val createdTimestamp: Long,
    val fileSizeBytes: Long,
    val pageCount: Int,
    val thumbnail: ContentRef?,
)
