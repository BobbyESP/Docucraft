/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.widgets

import com.bobbyesp.docucraft.feature.docscanner.domain.model.DocumentThumbnail

/**
 * A document as a home-screen widget lists it: only what a row shows, so that a widget is drawn
 * again when one of these changes and not when anything else about the document does.
 *
 * @property lastUsedAtEpochMillis When it was last opened, or saved if it never was.
 * @property pageCount `null` while its pages have not been counted.
 */
data class WidgetDocument(
    val uuid: String,
    val name: String,
    val pageCount: Int?,
    val lastUsedAtEpochMillis: Long,
    val thumbnail: DocumentThumbnail,
)
