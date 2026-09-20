/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.bobbyesp.docucraft.core.presentation.navigation.motion.rememberNavigationMotion
import com.bobbyesp.docucraft.core.presentation.navigation.overlay.rememberOverlaySceneStrategy
import com.bobbyesp.docucraft.core.presentation.navigation.pane.sharingTheWindow
import com.bobbyesp.docucraft.core.presentation.screens.preferences.settingsSection
import com.bobbyesp.docucraft.feature.docscanner.navigation.Home
import com.bobbyesp.docucraft.feature.docscanner.navigation.ScanRequestNavigation
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.homeSection
import com.bobbyesp.docucraft.feature.pdfviewer.navigation.PdfViewer
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.pdfViewerSection

/**
 * The app shell: a single back stack rendered by a single [NavDisplay].
 *
 * - The back stack is the whole navigation state. It survives configuration changes and process
 *   death via [rememberNavBackStack], and back (including the predictive gesture) simply pops it —
 *   an open document is closed, never the app, because the stack is only ever exited at Home.
 * - Scene strategies decide how the stack is arranged: overlays first, then the list-detail layout.
 *   The list-detail one lets Home and the PDF viewer share the screen on expanded windows; on
 *   compact windows they behave as a regular stack. Everything emerges from the same back stack —
 *   there is no separate "tablet navigation", and no second stack for sheets.
 * - Feature sections contribute their entries via [entryProvider] and say where they want to go
 *   through a [Navigator]. This file therefore no longer knows the shape of the navigation graph: a
 *   new destination is a new key and a new `entry`, neither of which lives here.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun DocucraftApp(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(Home)
    val navigator = rememberNavigator(backStack)
    val overlayStrategy = rememberOverlaySceneStrategy<NavKey>()
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>()
    val motion = rememberNavigationMotion()

    // Overlays first: the first strategy to claim the topmost entry wins, and a sheet or dialog has
    // to be recognised before the layout strategies try to give it a pane.
    //
    // The list-detail one is wrapped so the destinations it lays out side by side are told so. It
    // claims the stack only when two panes really fit, so everything it declines falls through to
    // the single-pane fallback — where `LocalPaneContext`'s default already says the right thing.
    val sceneStrategies =
        remember(overlayStrategy, listDetailStrategy) {
            listOf(overlayStrategy, listDetailStrategy.sharingTheWindow())
        }

    val openDocumentId by remember { derivedStateOf { backStack.openDocumentId() } }

    ScanRequestNavigation(navigator)

    NavDisplay(
        backStack = backStack,
        modifier = modifier.fillMaxSize(),
        onBack = navigator::goBack,
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        sceneStrategies = sceneStrategies,
        entryProvider =
            entryProvider {
                homeSection(navigator, selectedDocumentId = openDocumentId)

                pdfViewerSection(navigator)

                settingsSection(navigator)
            },
        transitionSpec = { motion.forward() },
        popTransitionSpec = { motion.backward() },
        predictivePopTransitionSpec = { motion.predictiveBack() },
    )
}

/**
 * The document showing in the detail pane, for the list to mark as selected.
 *
 * The topmost viewer entry anywhere in the stack, not the top of the stack itself. Requiring it to
 * be on top confused "above" with "instead of": opening a document's actions puts an overlay on the
 * stack, and an overlay floats over the layout rather than replacing it, so the viewer is still on
 * screen underneath and the list would drop the highlight of the very document the sheet is about.
 *
 * Anything that genuinely replaces the layout — settings, which is a scene of its own — takes the
 * document list off screen with it, so a highlight left pointing at a buried viewer is one nobody
 * can see, and it is correct again by the time the list comes back.
 */
internal fun List<NavKey>.openDocumentId(): String? =
    filterIsInstance<PdfViewer>().lastOrNull()?.documentUuid
