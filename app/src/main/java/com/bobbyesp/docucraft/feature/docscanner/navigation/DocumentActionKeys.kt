/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Acting on a single document: the grid of actions, and the two screens reachable from it.
 *
 * Real keys rather than pages of a private stack inside a sheet, so they are saved with the back
 * stack like anything else. Each carries the document's identity rather than the document, for the
 * same reason [PdfViewer] does. Restoration is by reflection over the class names; see
 * `proguard-rules.pro` before moving them.
 */
@Serializable data class DocumentActions(val documentUuid: String) : NavKey

/** Editing a document's title and description. */
@Serializable data class EditDocument(val documentUuid: String) : NavKey

/** Confirming a document's deletion. */
@Serializable data class DeleteDocument(val documentUuid: String) : NavKey
