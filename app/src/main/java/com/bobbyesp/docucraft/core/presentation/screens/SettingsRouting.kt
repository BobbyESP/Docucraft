/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import com.bobbyesp.docucraft.core.presentation.navigation.Route
import com.bobbyesp.docucraft.core.presentation.screens.preferences.SettingsScreen
import com.bobbyesp.docucraft.core.presentation.screens.preferences.appearance.AppearanceScreen
import com.revenuecat.purchases.ui.revenuecatui.customercenter.CustomerCenter

@Suppress("ModifierRequired")
@Composable
fun EntryProviderScope<Route>.SettingsRouting(onNavigate: (Route) -> Unit, onBack: () -> Unit) {
    entry<Route.Settings> {
        SettingsScreen(
            modifier = Modifier,
            onBack = { onBack() },
            onNavigate = { route -> onNavigate(route) },
        )
    }

    entry<Route.Settings.Appearance> { AppearanceScreen(onBack = { onBack() }) }

    entry<Route.Settings.CustomerCenter> {
        CustomerCenter(modifier = Modifier.fillMaxSize(), onDismiss = { onBack() })
    }
}
