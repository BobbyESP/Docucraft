/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.screens.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.navigation.Navigator
import com.bobbyesp.docucraft.core.presentation.navigation.pane.LocalPaneContext
import com.bobbyesp.docucraft.core.presentation.screens.preferences.appearance.AppearanceScreen
import com.bobbyesp.docucraft.core.presentation.screens.preferences.navigation.AppearanceSettings
import com.bobbyesp.docucraft.core.presentation.screens.preferences.navigation.Settings
import com.bobbyesp.docucraft.core.presentation.screens.preferences.navigation.SubscriptionSettings
import com.revenuecat.purchases.ui.revenuecatui.customercenter.CustomerCenter

/**
 * Settings and its sub-screens, as a list-detail pair of their own.
 *
 * They need a pane role at all — `ListDetailSceneStrategy` abandons the whole scene when the
 * topmost entry has none, so on a tablet settings used to take the entire window and an open
 * document vanished. And they need their *own* scene key: settings is a different list from the
 * document catalogue, which is left alone underneath rather than extended.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.settingsSection(navigator: Navigator) {
    entry<Settings>(
        metadata =
            ListDetailSceneStrategy.listPane(
                sceneKey = SettingsScene,
                detailPlaceholder = { NoSettingOpenPane() },
            )
    ) {
        SettingsScreen(
            onOpenAppearance = { navigator.goTo(AppearanceSettings) },
            onOpenCustomerCenter = { navigator.goTo(SubscriptionSettings) },
            onBack = navigator::leaveSettings,
        )
    }

    entry<AppearanceSettings>(metadata = ListDetailSceneStrategy.detailPane(SettingsScene)) {
        AppearanceScreen(
            onBack = navigator::goBack,
            // Beside the settings list there is already a way back on screen; filling the window
            // there is not. The scene knows which of the two happened; this does not have to.
            showBackButton = LocalPaneContext.current.providesOwnBackAffordance,
        )
    }

    entry<SubscriptionSettings>(metadata = ListDetailSceneStrategy.detailPane(SettingsScene)) {
        CustomerCenter(modifier = Modifier.fillMaxSize(), onDismiss = navigator::goBack)
    }
}

/**
 * Leaves the settings area entirely, however deep into it the user went.
 *
 * Only a wide window shows why `goBack` is wrong here: the list and a detail are on screen at once,
 * so popping one entry closes the pane the user was not pointing at and leaves them where they
 * were. Internal so the rule can be asserted without a composition.
 */
internal fun Navigator.leaveSettings() {
    goBackWhile { it is AppearanceSettings || it is SubscriptionSettings }

    goBack()
}

/**
 * Without it both fold into one scene and settings becomes the detail pane of the document list.
 */
private const val SettingsScene = "settings"

/** Shown beside the settings list on expanded windows while no setting is open. */
@Composable
private fun NoSettingOpenPane(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector = Icons.Rounded.Tune,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.select_setting_to_open),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
