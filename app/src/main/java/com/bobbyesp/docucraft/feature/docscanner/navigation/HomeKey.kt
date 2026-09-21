/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * The catalogue of scanned documents, and the app's root destination.
 *
 * Keys live with the feature that owns the destination, so navigating here means depending on this
 * feature — the honest edge. Restoration is by reflection over the class name; see
 * `proguard-rules.pro` before moving it.
 */
@Serializable data object Home : NavKey
