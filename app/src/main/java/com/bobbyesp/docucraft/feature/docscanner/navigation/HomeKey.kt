/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * The catalogue of scanned documents, and the app's root destination.
 *
 * Keys live with the feature that owns the destination rather than in a single shared file: a
 * feature that cannot name its own identity is not really separable from the ones beside it, and
 * anything wanting to navigate here has to depend on this feature, which is the honest edge.
 *
 * Restoration is by reflection over this class's name — see `proguard-rules.pro` before moving it.
 */
@Serializable data object Home : NavKey
