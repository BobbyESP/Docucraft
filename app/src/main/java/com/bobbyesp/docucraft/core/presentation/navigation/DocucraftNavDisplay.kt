/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.navigation

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.bobbyesp.docucraft.core.presentation.navigation.motion.LocalNavSharedTransitionScope
import com.bobbyesp.docucraft.core.presentation.navigation.motion.outOfFocusBehindOverlay
import com.bobbyesp.docucraft.core.presentation.navigation.motion.rememberNavigationMotion
import com.bobbyesp.docucraft.core.presentation.navigation.overlay.OverlaySceneStrategy

/**
 * The one way this app renders a back stack, for every host that has one: `MainActivity`'s shell
 * and `PdfViewerActivity`'s own. They differ in which destinations and scene strategies they
 * register, never in how an entry keeps its state or how it moves, so those live here.
 *
 * - Saveable state before the `ViewModelStore`: the library requires that order for a
 *   `SavedStateHandle` to work, which is what lets ViewModels survive process death.
 * - Transitions come from [rememberNavigationMotion] alone; no destination contributes its own.
 *   Elements shared between two destinations animate in the one [SharedTransitionLayout] here.
 * - Back pops the stack through [navigator]; at the last entry the display leaves it to the
 *   activity.
 * - While a sheet or a dialog is open, what is behind it is out of focus
 *   ([outOfFocusBehindOverlay]), whichever destination opened it.
 */
@Composable
fun DocucraftNavDisplay(
    backStack: NavBackStack<NavKey>,
    navigator: Navigator,
    sceneStrategies: List<SceneStrategy<NavKey>>,
    entryProvider: (NavKey) -> NavEntry<NavKey>,
    modifier: Modifier = Modifier,
) {
    val motion = rememberNavigationMotion()

    // Asked of the entry, as the overlay strategy asks: building it is cheap, and its content is
    // not composed here.
    val top = backStack.lastOrNull()
    val topEntry = remember(top, entryProvider) { top?.let(entryProvider) }
    val overlayShowing =
        topEntry != null && OverlaySceneStrategy.isShownAsOverlay(topEntry, backStack.size)

    // Handed out through a local rather than to `NavDisplay`: given the scope, the display wraps
    // every entry in a shared element of its own, and each destination would start gliding between
    // panes. Only the elements that ask should travel.
    SharedTransitionLayout(
        modifier = modifier.fillMaxSize().outOfFocusBehindOverlay(overlayShowing)
    ) {
        CompositionLocalProvider(LocalNavSharedTransitionScope provides this) {
            NavDisplay(
                backStack = backStack,
                modifier = Modifier.fillMaxSize(),
                onBack = navigator::goBack,
                entryDecorators =
                    listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                sceneStrategies = sceneStrategies,
                entryProvider = entryProvider,
                transitionSpec = { motion.forward() },
                popTransitionSpec = { motion.backward() },
                predictivePopTransitionSpec = { motion.predictiveBack() },
            )
        }
    }
}
