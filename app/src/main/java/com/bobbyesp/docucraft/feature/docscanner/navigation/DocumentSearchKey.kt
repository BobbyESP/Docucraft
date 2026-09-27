/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Searching the catalogue, reached from Home's search bar.
 *
 * A destination rather than a state of Home: back closes it like any other screen, a document
 * opened from its results comes back to them, and on a wide window it takes the list's place beside
 * the open document. The query rides in its ViewModel's `SavedStateHandle`, not here, so typing
 * does not rewrite the back stack. Restoration is by reflection over the class name; see
 * `proguard-rules.pro`.
 */
@Serializable data object DocumentSearch : NavKey
