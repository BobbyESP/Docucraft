/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.navigation

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.bobbyesp.docucraft.core.presentation.screens.preferences.settingsSection
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.homeSection
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.pdfViewerSection

/**
 * The app shell: a single back stack rendered by a single [NavDisplay].
 *
 * - The back stack is the whole navigation state. It survives configuration changes and process
 *   death via [rememberNavBackStack], and back (including the predictive gesture) simply pops it —
 *   an open document is closed, never the app, because the stack is only ever exited at Home.
 * - The list-detail scene strategy lets Home and the PDF viewer share the screen on expanded
 *   windows; on compact windows they behave as a regular stack. Both emerge from the same back
 *   stack — there is no separate "tablet navigation".
 * - Feature sections contribute their entries via [entryProvider]; screens only emit events.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun DocucraftApp(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(Route.Home)
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>()

    val goBack: () -> Unit = { backStack.removeLastOrNull() }

    // The document currently open in the detail pane, used to highlight it in the list.
    val openDocumentId = remember {
        backStack.filterIsInstance<Route.PdfViewer>().lastOrNull()?.document?.uuid
    }

    NavDisplay(
        backStack = backStack,
        modifier = modifier.fillMaxSize(),
        onBack = goBack,
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        sceneStrategies = listOf(listDetailStrategy),
        entryProvider =
            entryProvider {
                homeSection(
                    selectedDocumentId = openDocumentId,
                    onOpenDocument = { document -> backStack.add(Route.PdfViewer(document)) },
                    onOpenSettings = { backStack.add(Route.Settings) },
                )

                pdfViewerSection(onBack = goBack)

                settingsSection(
                    onOpenAppearance = { backStack.add(Route.Settings.Appearance) },
                    onOpenCustomerCenter = { backStack.add(Route.Settings.CustomerCenter) },
                    onBack = goBack,
                )
            },
        transitionSpec = {
            // Slide in from right when navigating forward
            slideInHorizontally(initialOffsetX = { it }) togetherWith
                slideOutHorizontally(targetOffsetX = { -it })
        },
        popTransitionSpec = {
            // Slide in from left when navigating back
            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                slideOutHorizontally(targetOffsetX = { it })
        },
        predictivePopTransitionSpec = {
            // Slide in from left when navigating back
            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                slideOutHorizontally(targetOffsetX = { it })
        },
    )
}
