/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.navigation

import androidx.navigation3.runtime.NavKey
import com.bobbyesp.docucraft.feature.shared.domain.BasicDocument
import kotlinx.serialization.Serializable

/**
 * Every destination the user can navigate to. Routes are pure, serializable data: they carry only
 * the arguments a destination needs, never UI concerns (icons, labels) or behavior.
 */
@Serializable
sealed interface Route : NavKey {

    @Serializable data object Home : Route

    @Serializable data class PdfViewer(val document: BasicDocument) : Route

    @Serializable
    data object Settings : Route {

        @Serializable data object Appearance : Route

        @Serializable data object CustomerCenter : Route
    }
}
