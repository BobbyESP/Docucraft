/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Acting on a single document: the grid of actions, and the two screens reachable from it.
 *
 * These were pages of a second, private back stack held in `HomeViewModel`'s state and rendered by
 * a `NavDisplay` of their own inside a bottom sheet. That stack was neither serializable nor saved,
 * so killing the process with the sheet open lost it — the uuid was written to the
 * `SavedStateHandle` under `active_sheet_doc_id` and then never read by anything.
 *
 * As real keys they are simply part of the back stack, which is saved already. Each carries the
 * document's identity rather than the document, for the same reason [PdfViewer] does: the entry
 * outlives any particular reading of it.
 *
 * Restoration is by reflection over these class names — see `proguard-rules.pro` before moving
 * them.
 */
@Serializable data class DocumentActions(val documentUuid: String) : NavKey

/** Editing a document's title and description. */
@Serializable data class EditDocument(val documentUuid: String) : NavKey

/** Confirming a document's deletion. */
@Serializable data class DeleteDocument(val documentUuid: String) : NavKey
