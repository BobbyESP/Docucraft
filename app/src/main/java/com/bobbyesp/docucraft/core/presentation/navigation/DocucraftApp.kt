/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.navigation

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.bobbyesp.docucraft.core.presentation.common.LocalWindowWidthState
import com.bobbyesp.docucraft.core.presentation.screens.preferences.settingsSection
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.homeSection
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.pdfViewerSection

/**
 * The app shell: adaptive navigation UI plus a single [NavDisplay] rendering the navigation state.
 *
 * - [NavigationSuiteScaffold] picks the right navigation component (bottom bar, rail, drawer) for
 *   the current window size.
 * - The list-detail scene strategy lets Home and the PDF viewer share the screen on expanded
 *   windows; on compact windows they behave as a regular stack. Both emerge from the same back
 *   stack — there is no separate "tablet navigation".
 * - Feature sections contribute their entries via [entryProvider]; screens only emit events.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun DocucraftApp(modifier: Modifier = Modifier) {
    val navigationState = rememberNavigationState()
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>()

    // The document currently open in the detail pane, used to highlight it in the list.
    val homeBackStack = navigationState.backStacks.getValue(TopLevelDestination.Home)
    val openDocumentId =
        homeBackStack.filterIsInstance<Route.PdfViewer>().lastOrNull()?.document?.uuid

    val entries =
        navigationState.rememberDecoratedEntries(
            entryProvider =
                entryProvider {
                    homeSection(
                        selectedDocumentId = openDocumentId,
                        onOpenDocument = { document ->
                            navigationState.navigateTo(Route.PdfViewer(document))
                        },
                        onOpenSettings = {
                            navigationState.switchTo(TopLevelDestination.Settings)
                        },
                    )

                    pdfViewerSection(onBack = navigationState::goBack)

                    settingsSection(
                        onOpenAppearance = {
                            navigationState.navigateTo(Route.Settings.Appearance)
                        },
                        onOpenCustomerCenter = {
                            navigationState.navigateTo(Route.Settings.CustomerCenter)
                        },
                        onBack = navigationState::goBack,
                    )
                }
        )

    // Give the PDF viewer the full screen on phones; larger windows keep their rail/drawer.
    val isCompact = LocalWindowWidthState.current == WindowWidthSizeClass.Compact
    val isViewingPdf = navigationState.currentBackStack.lastOrNull() is Route.PdfViewer
    val layoutType =
        if (isCompact && isViewingPdf) {
            NavigationSuiteType.None
        } else {
            NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfo())
        }

    NavigationSuiteScaffold(
        modifier = modifier,
        layoutType = layoutType,
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                item(
                    selected = destination == navigationState.currentDestination,
                    onClick = { navigationState.switchTo(destination) },
                    icon = { Icon(destination.icon, contentDescription = null) },
                    label = { Text(stringResource(destination.labelRes)) },
                )
            }
        },
    ) {
        NavDisplay(
            entries = entries,
            modifier = Modifier.fillMaxSize(),
            onBack = navigationState::goBack,
            sceneStrategies = listOf(listDetailStrategy),
            transitionSpec = {
                slideInHorizontally(initialOffsetX = { it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { -it })
            },
            popTransitionSpec = {
                slideInHorizontally(initialOffsetX = { -it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { it })
            },
        )
    }
}
