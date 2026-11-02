/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.navigation.keys

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * An open document, by identity only: the viewer reads it from the catalogue, so an entry cannot go
 * stale while the user edits the document it points at — routine on expanded windows, where the
 * list stays beside it. Restoration is by reflection over the class name; see `proguard-rules.pro`.
 *
 * @property searchQuery what the user searched for to reach the document; the viewer opens its find
 *   bar on it, so the matches show at once. `null` opens the document plainly.
 */
@Serializable
data class PdfViewer(val documentUuid: String, val searchQuery: String? = null) : NavKey
