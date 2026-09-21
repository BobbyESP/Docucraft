/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * An open document, by identity only: the viewer reads it from the catalogue, so an entry cannot go
 * stale while the user edits the document it points at — routine on expanded windows, where the
 * list stays beside it. Restoration is by reflection over the class name; see `proguard-rules.pro`.
 */
@Serializable data class PdfViewer(val documentUuid: String) : NavKey
