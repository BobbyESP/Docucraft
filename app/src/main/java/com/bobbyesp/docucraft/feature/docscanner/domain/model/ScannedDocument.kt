/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.model

import com.bobbyesp.scanner.ContentRef

/**
 * A document the app has scanned and catalogued.
 *
 * @property id Row identifier assigned by the catalogue.
 * @property uuid Stable identifier used to refer to the document across screens.
 * @property filename Name without extension, e.g. `Scan_20260919_142530`.
 * @property title User-supplied title, if they gave one.
 * @property description User-supplied description, if they gave one.
 * @property path Where the document lives.
 * @property createdTimestamp When it was captured, in epoch milliseconds.
 * @property fileSize Size in bytes.
 * @property pageCount Pages in the document.
 * @property thumbnail Where its preview image lives, if one could be produced.
 */
data class ScannedDocument(
    val id: Long,
    val uuid: String,
    val filename: String,
    val title: String?,
    val description: String?,
    val path: ContentRef,
    val createdTimestamp: Long,
    val fileSize: Long,
    val pageCount: Int,
    val thumbnail: ContentRef?,
)
