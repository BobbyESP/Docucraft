/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.shared.domain

import kotlinx.serialization.Serializable

/**
 * The little a PDF viewer needs to know about what it is showing.
 *
 * Deliberately not [com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument]: the
 * viewer also opens documents this app has never seen, handed to it by other apps, and those have
 * no catalogue entry, no page count and no preview. `PdfViewerActivity` makes one of these up on
 * the spot for them.
 *
 * @property uuid Identifies the document. Synthetic for documents from outside the app.
 * @property filename Shown when there is no [title].
 * @property uri Where to read the document from.
 */
@Serializable
data class BasicDocument(
    val uuid: String,
    val filename: String,
    val uri: String,
    val title: String? = null,
    val description: String? = null,
)
