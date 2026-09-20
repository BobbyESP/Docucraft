/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The back stack is plain state, so the rules about what may be done to it have to live somewhere.
 * They live in [BackStackNavigator], and this is what stops them from quietly disappearing.
 */
class NavigatorTest {

    private fun backStack(vararg keys: NavKey) = NavBackStack(*keys)

    @Test
    fun `going somewhere puts it on top, leaving the rest to come back to`() {
        val stack = backStack(Route.Home)

        BackStackNavigator(stack).goTo(document)

        assertEquals(listOf(Route.Home, document), stack.toList())
    }

    /** A double tap on a list item used to open the same document twice. */
    @Test
    fun `going to the destination already on top does nothing`() {
        val stack = backStack(Route.Home, document)

        BackStackNavigator(stack).goTo(document)

        assertEquals(listOf(Route.Home, document), stack.toList())
    }

    @Test
    fun `the same destination is still reachable from somewhere else`() {
        val stack = backStack(Route.Home, document, Route.Settings)

        BackStackNavigator(stack).goTo(document)

        assertEquals(listOf(Route.Home, document, Route.Settings, document), stack.toList())
    }

    @Test
    fun `going back leaves the current destination`() {
        val stack = backStack(Route.Home, document)

        BackStackNavigator(stack).goBack()

        assertEquals(listOf(Route.Home), stack.toList())
    }

    /**
     * `NavDisplay` requires a non-empty back stack and throws on an empty one. The system never
     * asks to pop the root — the back handler is disabled there — but a screen calling `goBack`
     * once too often would otherwise take the app down with it.
     */
    @Test
    fun `going back at the root does nothing rather than emptying the stack`() {
        val stack = backStack(Route.Home)

        BackStackNavigator(stack).goBack()

        assertEquals(listOf(Route.Home), stack.toList())
    }

    private companion object {
        val document = Route.PdfViewer(documentUuid = "doc-1")
    }
}
