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
import com.bobbyesp.docucraft.core.presentation.screens.preferences.appearance.AppearanceScreen
import com.bobbyesp.docucraft.core.presentation.screens.preferences.navigation.AppearanceSettings
import com.bobbyesp.docucraft.core.presentation.screens.preferences.navigation.Settings
import com.bobbyesp.docucraft.core.presentation.screens.preferences.navigation.SubscriptionSettings
import com.revenuecat.purchases.ui.revenuecatui.customercenter.CustomerCenter

/**
 * Settings and its sub-screens, laid out as a list-detail pair of their own.
 *
 * These destinations used to declare no pane role at all, which had a consequence nobody could see
 * on a phone: `ListDetailSceneStrategy` reads the pane metadata of the topmost entry and abandons
 * the whole scene when it finds none. Opening settings on a tablet therefore threw away the
 * list-detail layout underneath and took the entire window — with an open document vanishing behind
 * a screen of toggles.
 *
 * Giving them a scene key of their own says what was always true: settings is a list with detail
 * screens hanging off it, and it is a *different* list from the document catalogue. The documents
 * scene is left alone underneath rather than being extended.
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
            onBack = navigator::goBack,
        )
    }

    entry<AppearanceSettings>(metadata = ListDetailSceneStrategy.detailPane(SettingsScene)) {
        AppearanceScreen(onBack = navigator::goBack)
    }

    entry<SubscriptionSettings>(metadata = ListDetailSceneStrategy.detailPane(SettingsScene)) {
        CustomerCenter(modifier = Modifier.fillMaxSize(), onDismiss = navigator::goBack)
    }
}

/**
 * Groups these entries into a scaffold separate from the document catalogue's. Without it both
 * would fold into one scene and settings would appear as the detail pane of the document list.
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
