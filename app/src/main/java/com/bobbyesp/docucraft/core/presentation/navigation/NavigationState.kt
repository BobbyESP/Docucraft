/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator

/**
 * Creates the app's navigation state: one [NavBackStack] per [TopLevelDestination] plus the
 * currently selected destination. Everything survives configuration changes and process death —
 * [rememberNavBackStack] persists the stacks via their [NavKey] serializers.
 */
@Composable
fun rememberNavigationState(
    startDestination: TopLevelDestination = TopLevelDestination.Home
): NavigationState {
    val currentDestination = rememberSaveable { mutableStateOf(startDestination) }
    val backStacks =
        TopLevelDestination.entries.associateWith { destination ->
            rememberNavBackStack(destination.rootRoute)
        }

    return remember { NavigationState(startDestination, currentDestination, backStacks) }
}

/**
 * The single source of truth for navigation. Navigating is just mutating a list: screens never see
 * this class — they emit events that the app shell translates into calls on it.
 *
 * Back behavior follows the "exit through home" pattern: popping past the root of a secondary
 * top-level destination returns to [startDestination], and the app is only ever exited from there.
 */
@Stable
class NavigationState
internal constructor(
    val startDestination: TopLevelDestination,
    currentDestinationState: MutableState<TopLevelDestination>,
    val backStacks: Map<TopLevelDestination, NavBackStack<NavKey>>,
) {
    var currentDestination: TopLevelDestination by currentDestinationState
        private set

    val currentBackStack: NavBackStack<NavKey>
        get() = backStacks.getValue(currentDestination)

    /** Pushes [route] onto the back stack of the current top-level destination. */
    fun navigateTo(route: Route) {
        currentBackStack.add(route)
    }

    /** Switches to another top-level destination, keeping each destination's stack intact. */
    fun switchTo(destination: TopLevelDestination) {
        currentDestination = destination
    }

    fun goBack() {
        if (currentBackStack.size > 1) {
            currentBackStack.removeLastOrNull()
        } else if (currentDestination != startDestination) {
            currentDestination = startDestination
        }
    }

    /**
     * Converts the visible back stacks into decorated [NavEntry]s for `NavDisplay`. Each top-level
     * destination gets its own saveable-state and ViewModel-store decorators, so tab content and
     * ViewModels are retained while a tab is inactive.
     */
    @Composable
    fun rememberDecoratedEntries(
        entryProvider: (NavKey) -> NavEntry<NavKey>
    ): List<NavEntry<NavKey>> {
        val decoratedEntries = backStacks.mapValues { (_, backStack) ->
            val decorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
                    rememberViewModelStoreNavEntryDecorator<NavKey>(),
                )
            rememberDecoratedNavEntries(
                backStack = backStack,
                entryDecorators = decorators,
                entryProvider = entryProvider,
            )
        }

        return destinationsInUse().flatMap { decoratedEntries.getValue(it) }
    }

    /**
     * The start destination's entries are always at the base of the display ("exit through home");
     * at most one other destination sits on top of it.
     */
    private fun destinationsInUse(): List<TopLevelDestination> =
        if (currentDestination == startDestination) {
            listOf(startDestination)
        } else {
            listOf(startDestination, currentDestination)
        }
}
