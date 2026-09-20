/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * An open document.
 *
 * Carries only the document's identity. The viewer reads the document itself from the catalogue, so
 * an entry sitting in the back stack cannot go stale while the user edits the document it points at
 * — which happens routinely on expanded windows, where the list stays on screen beside it.
 *
 * Restoration is by reflection over this class's name — see `proguard-rules.pro` before moving it.
 */
@Serializable data class PdfViewer(val documentUuid: String) : NavKey
