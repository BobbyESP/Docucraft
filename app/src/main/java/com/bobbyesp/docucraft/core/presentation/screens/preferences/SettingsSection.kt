/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.screens.preferences

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.docucraft.core.presentation.navigation.Route
import com.bobbyesp.docucraft.core.presentation.screens.preferences.appearance.AppearanceScreen
import com.revenuecat.purchases.ui.revenuecatui.customercenter.CustomerCenter

/** Settings and its sub-screens. */
fun EntryProviderScope<NavKey>.settingsSection(
    onOpenAppearance: () -> Unit,
    onOpenCustomerCenter: () -> Unit,
    onBack: () -> Unit,
) {
    entry<Route.Settings> {
        SettingsScreen(
            onOpenAppearance = onOpenAppearance,
            onOpenCustomerCenter = onOpenCustomerCenter,
            onBack = onBack,
        )
    }

    entry<Route.Settings.Appearance> { AppearanceScreen(onBack = onBack) }

    entry<Route.Settings.CustomerCenter> {
        CustomerCenter(modifier = Modifier.fillMaxSize(), onDismiss = onBack)
    }
}
