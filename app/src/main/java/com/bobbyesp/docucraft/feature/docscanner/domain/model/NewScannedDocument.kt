/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.model

import com.bobbyesp.scanner.ContentRef

/**
 * A document that has been stored but not yet catalogued.
 *
 * The write-side counterpart of [ScannedDocument]. It lacks exactly `uuid`, `title` and
 * `description`, which is the point: those are assigned by the catalogue or written by the user,
 * never produced by a scan. Everything else is named as it is named there.
 *
 * @property filename Name without extension, e.g. `Scan_20260919_142530`.
 * @property location Where the stored document lives.
 * @property capturedAtEpochMillis When it was scanned.
 * @property sizeBytes Size of the stored document.
 * @property pageCount Pages in the document.
 * @property thumbnail Where its preview image lives, if one could be produced.
 */
data class NewScannedDocument(
    val filename: String,
    val location: ContentRef,
    val capturedAtEpochMillis: Long,
    val sizeBytes: Long,
    val pageCount: Int,
    val thumbnail: ContentRef?,
)
