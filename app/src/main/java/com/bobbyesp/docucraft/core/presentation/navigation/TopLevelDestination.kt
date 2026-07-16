/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.bobbyesp.docucraft.R

/**
 * The app's top-level destinations, each owning its own back stack. This is what the adaptive
 * navigation UI (bottom bar / rail / drawer) renders.
 */
enum class TopLevelDestination(
    val rootRoute: Route,
    val icon: ImageVector,
    @param:StringRes val labelRes: Int,
) {
    Home(rootRoute = Route.Home, icon = Icons.Rounded.Home, labelRes = R.string.home),
    Settings(
        rootRoute = Route.Settings,
        icon = Icons.Rounded.Settings,
        labelRes = R.string.settings,
    ),
}
