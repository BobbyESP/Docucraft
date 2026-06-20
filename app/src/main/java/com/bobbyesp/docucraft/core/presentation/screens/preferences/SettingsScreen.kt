/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.screens.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.components.settings.SettingsGroup
import com.bobbyesp.docucraft.core.presentation.components.settings.SettingsItem
import com.bobbyesp.docucraft.core.presentation.navigation.Route
import com.bobbyesp.docucraft.core.presentation.screens.preferences.subscription.SubscriptionViewModel
import com.revenuecat.purchases.ui.revenuecatui.PaywallDialog
import com.revenuecat.purchases.ui.revenuecatui.PaywallDialogOptions
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    onNavigate: (Route) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subscriptionViewModel: SubscriptionViewModel = koinViewModel(),
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val isPro by subscriptionViewModel.isPro.collectAsStateWithLifecycle()
    var showPaywall by remember { mutableStateOf(false) }

    if (showPaywall) {
        PaywallDialog(
            paywallDialogOptions =
                PaywallDialogOptions.Builder().setDismissRequest { showPaywall = false }.build()
        )
    }

    val subscriptionItem =
        if (isPro) {
            SettingsItem(
                title = stringResource(R.string.manage_subscription),
                supportingText = stringResource(R.string.manage_subscription_desc),
                icon = Icons.Rounded.Star,
                onClick = { onNavigate(Route.Settings.CustomerCenter) },
            )
        } else {
            SettingsItem(
                title = stringResource(R.string.upgrade_to_pro),
                supportingText = stringResource(R.string.upgrade_to_pro_desc),
                icon = Icons.Rounded.Star,
                onClick = { showPaywall = true },
            )
        }

    val settings: PersistentList<SettingsItem> =
        persistentListOf(
            // subscriptionItem, TODO: Uncomment this when the subscription feature is ready
            SettingsItem(
                title = stringResource(R.string.appearance),
                supportingText = stringResource(R.string.appearance_desc),
                icon = Icons.Rounded.ColorLens,
                onClick = { onNavigate(Route.Settings.Appearance) },
            )
        )

    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(text = stringResource(R.string.settings)) },
                modifier = Modifier,
                navigationIcon = {
                    IconButton(
                        shape = CircleShape,
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                        onClick = { onBack() },
                        content = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = null,
                            )
                        },
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(32.dp),
        ) {
            item(contentType = "settings_category") {
                Text(
                    text = stringResource(R.string.general),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
            item(contentType = "settings_list") {
                SettingsGroup(modifier = Modifier, items = settings)
            }
        }
    }
}
