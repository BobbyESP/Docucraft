/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * The only thing a feature is given of the back stack.
 *
 * Deliberately not the stack itself: a feature has no business reading it, reordering it, or
 * deciding what sits underneath. Saying where it wants to go is enough, and spares the shell a
 * callback per edge of the graph.
 */
@Stable
interface Navigator {

    /** Goes to [key]. Asking for the destination already on top does nothing. */
    fun goTo(key: NavKey)

    /** Leaves the current destination. At the last one this does nothing rather than emptying. */
    fun goBack()

    /**
     * Leaves every destination on top that [predicate] accepts, for dismissing a related group at
     * once — a confirmation and the menu that opened it. Counting [goBack] calls instead would
     * depend on how the user got there.
     */
    fun goBackWhile(predicate: (NavKey) -> Boolean)

    /**
     * Takes [key] off the stack wherever it sits, for a destination whose subject has ceased to
     * exist. Not [goBack]: wanting yourself gone is not asking to pop whatever is on top, which
     * need not be you.
     */
    fun removeDestination(key: NavKey)
}

@Composable
fun rememberNavigator(backStack: NavBackStack<NavKey>): Navigator =
    remember(backStack) { BackStackNavigator(backStack) }

/**
 * The [Navigator] over a back stack. Public so that its rules, and those of the navigation built on
 * it in the app and in features, can be asserted without a composition. A feature never builds one:
 * it is handed a [Navigator], and the back stack it would need belongs to the app's shell.
 */
class BackStackNavigator(private val backStack: NavBackStack<NavKey>) : Navigator {

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

    /**
     * Every occurrence: one destination can be reached twice, and a deleted document is gone from
     * both.
     */
    override fun removeDestination(key: NavKey) {
        while (backStack.size > 1 && backStack.contains(key)) backStack.remove(key)
    }
}
