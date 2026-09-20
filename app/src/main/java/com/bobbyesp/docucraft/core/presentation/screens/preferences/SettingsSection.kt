/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.screens.preferences

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.docucraft.core.presentation.navigation.Navigator
import com.bobbyesp.docucraft.core.presentation.screens.preferences.appearance.AppearanceScreen
import com.bobbyesp.docucraft.core.presentation.screens.preferences.navigation.AppearanceSettings
import com.bobbyesp.docucraft.core.presentation.screens.preferences.navigation.Settings
import com.bobbyesp.docucraft.core.presentation.screens.preferences.navigation.SubscriptionSettings
import com.revenuecat.purchases.ui.revenuecatui.customercenter.CustomerCenter

/** Settings and its sub-screens. */
fun EntryProviderScope<NavKey>.settingsSection(navigator: Navigator) {
    entry<Settings> {
        SettingsScreen(
            onOpenAppearance = { navigator.goTo(AppearanceSettings) },
            onOpenCustomerCenter = { navigator.goTo(SubscriptionSettings) },
            onBack = navigator::goBack,
        )
    }

    entry<AppearanceSettings> { AppearanceScreen(onBack = navigator::goBack) }

    entry<SubscriptionSettings> {
        CustomerCenter(modifier = Modifier.fillMaxSize(), onDismiss = navigator::goBack)
    }
}
