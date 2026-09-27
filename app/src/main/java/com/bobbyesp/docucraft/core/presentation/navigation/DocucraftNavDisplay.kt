/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.bobbyesp.docucraft.core.presentation.navigation.motion.rememberNavigationMotion

/**
 * The one way this app renders a back stack, for every host that has one: `MainActivity`'s shell
 * and `PdfViewerActivity`'s own. They differ in which destinations and scene strategies they
 * register, never in how an entry keeps its state or how it moves, so those live here.
 *
 * - Saveable state before the `ViewModelStore`: the library requires that order for a
 *   `SavedStateHandle` to work, which is what lets ViewModels survive process death.
 * - Transitions come from [rememberNavigationMotion] alone; no destination contributes its own.
 * - Back pops the stack through [navigator]; at the last entry the display leaves it to the
 *   activity.
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
        entryProvider = entryProvider,
        transitionSpec = { motion.forward() },
        popTransitionSpec = { motion.backward() },
        predictivePopTransitionSpec = { motion.predictiveBack() },
    )
}
