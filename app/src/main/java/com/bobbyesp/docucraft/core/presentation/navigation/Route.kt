/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Every destination the user can navigate to. Routes are pure, serializable data: they carry only
 * the arguments a destination needs, never UI concerns (icons, labels) or behavior.
 */
@Serializable
sealed interface Route : NavKey {

    @Serializable data object Home : Route

    /**
     * Carries only the document's identity. The viewer reads the document itself from the
     * catalogue, so an entry sitting in the back stack cannot go stale while the user edits the
     * document it points at.
     */
    @Serializable data class PdfViewer(val documentUuid: String) : Route

    @Serializable
    data object Settings : Route {

        @Serializable data object Appearance : Route

        @Serializable data object CustomerCenter : Route
    }
}
