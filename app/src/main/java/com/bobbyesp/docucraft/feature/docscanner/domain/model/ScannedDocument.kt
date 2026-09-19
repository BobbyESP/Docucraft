/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.model

import com.bobbyesp.scanner.ContentRef

/**
 * A document the app has scanned and catalogued.
 *
 * Identified by [uuid] alone. The catalogue keeps a row id of its own, but nothing above the data
 * layer has ever needed it, so it does not travel.
 *
 * @property uuid Stable identifier, used to refer to the document everywhere.
 * @property filename Name without extension, e.g. `Scan_20260919_142530`.
 * @property title User-supplied title, if they gave one.
 * @property description User-supplied description, if they gave one.
 * @property location Where the document lives.
 * @property capturedAtEpochMillis When it was scanned.
 * @property sizeBytes Size of the document.
 * @property pageCount Pages in the document.
 * @property thumbnail Where its preview image lives, if one could be produced.
 */
data class ScannedDocument(
    val uuid: String,
    val filename: String,
    val title: String?,
    val description: String?,
    val location: ContentRef,
    val capturedAtEpochMillis: Long,
    val sizeBytes: Long,
    val pageCount: Int,
    val thumbnail: ContentRef?,
)
