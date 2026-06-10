/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.docucraft.feature.shared.domain.BasicDocument
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Home : Route, TopLevelRoute {
        override val icon: ImageVector
            get() = Icons.Rounded.Home
    }

    @Serializable
    data object Settings : Route, TopLevelRoute {
        override val icon: ImageVector
            get() = Icons.Rounded.Settings

        @Serializable data object Appearance : Route
        @Serializable data object CustomerCenter : Route
    }

    @Serializable data class PdfViewer(val documentInfo: BasicDocument) : Route
}

interface TopLevelRoute {
    val icon: ImageVector
}

val TopLevelRoutes = listOf<TopLevelRoute>(Route.Home)
