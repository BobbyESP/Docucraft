/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.navigation

import androidx.navigation3.runtime.NavKey
import com.bobbyesp.docucraft.feature.pdfviewer.domain.model.ViewerDocumentRef
import kotlinx.serialization.Serializable

/**
 * An open document, by identity only: the viewer reads it from the catalogue, so an entry cannot go
 * stale while the user edits the document it points at — routine on expanded windows, where the
 * list stays beside it. Restoration is by reflection over the class name; see `proguard-rules.pro`.
 */
@Serializable data class PdfViewer(val documentUuid: String) : NavKey

/**
 * A document another app handed over, shown by `PdfViewerActivity` as the root of its own back
 * stack. Known by location: it has no catalogue entry.
 */
@Serializable data class ExternalPdfViewer(val uri: String, val displayName: String) : NavKey

/**
 * The details of a document open in a viewer, catalogued or external. A destination rather than a
 * sheet the viewer holds, so it survives rotation and process death and back closes it.
 */
@Serializable data class PdfDocumentDetails(val document: ViewerDocumentRef) : NavKey
