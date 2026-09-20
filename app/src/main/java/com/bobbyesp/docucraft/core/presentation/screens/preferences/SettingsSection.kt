/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.screens.preferences

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.docucraft.core.presentation.navigation.Navigator
import com.bobbyesp.docucraft.core.presentation.navigation.Route
import com.bobbyesp.docucraft.core.presentation.screens.preferences.appearance.AppearanceScreen
import com.revenuecat.purchases.ui.revenuecatui.customercenter.CustomerCenter

/** Settings and its sub-screens. */
fun EntryProviderScope<NavKey>.settingsSection(navigator: Navigator) {
    entry<Route.Settings> {
        SettingsScreen(
            onOpenAppearance = { navigator.goTo(Route.Settings.Appearance) },
            onOpenCustomerCenter = { navigator.goTo(Route.Settings.CustomerCenter) },
            onBack = navigator::goBack,
        )
    }

    entry<Route.Settings.Appearance> { AppearanceScreen(onBack = navigator::goBack) }

    entry<Route.Settings.CustomerCenter> {
        CustomerCenter(modifier = Modifier.fillMaxSize(), onDismiss = navigator::goBack)
    }
}
