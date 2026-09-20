/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * The only thing a feature is given of the back stack.
 *
 * Features used to receive one callback per edge of the navigation graph — `onOpenDocument`,
 * `onOpenSettings`, `onOpenAppearance` — which meant the shell had to know every edge, and adding a
 * destination meant editing a file that had nothing to do with it. A feature that can say where it
 * wants to go needs no such wiring.
 *
 * Deliberately not the back stack itself: a feature has no business reading the stack, reordering
 * it, or deciding what sits underneath it. Those are the shell's calls.
 */
@Stable
interface Navigator {

    /**
     * Goes to [key], leaving the current destination behind to come back to.
     *
     * Asking for the destination that is already on top does nothing, so a double tap on a list
     * item cannot open the same document twice.
     */
    fun goTo(key: NavKey)

    /** Leaves the current destination. At the last one this does nothing rather than emptying. */
    fun goBack()

    /**
     * Leaves every destination on top that [predicate] accepts, stopping at the first that it does
     * not.
     *
     * For dismissing a group of related destinations at once — a confirmation and the menu that
     * opened it, when the thing they were both about has ceased to exist. Doing that with repeated
     * [goBack] calls would mean counting, and the count depends on how the user got there.
     */
    fun goBackWhile(predicate: (NavKey) -> Boolean)
}

@Composable
fun rememberNavigator(backStack: NavBackStack<NavKey>): Navigator =
    remember(backStack) { BackStackNavigator(backStack) }

/** Internal rather than private so its rules can be asserted without a composition. */
internal class BackStackNavigator(private val backStack: NavBackStack<NavKey>) : Navigator {

    override fun goTo(key: NavKey) {
        if (backStack.lastOrNull() == key) return

        backStack.add(key)
    }

    /**
     * `NavDisplay` requires a non-empty back stack, so the root is not poppable. Refusing here
     * rather than in each caller means no destination has to know whether it is the root.
     */
    override fun goBack() {
        if (backStack.size <= 1) return

        backStack.removeLastOrNull()
    }

    override fun goBackWhile(predicate: (NavKey) -> Boolean) {
        while (backStack.size > 1 && predicate(backStack.last())) backStack.removeLastOrNull()
    }
}
