/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.navigation.keys

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Reviewing a scan that has just been saved: naming it and saying where it goes, while the user
 * still knows what it is. The scan is already a document; this only changes it.
 */
@Serializable data class ReviewScan(val documentUuid: String) : NavKey
