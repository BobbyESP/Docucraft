/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.docucraft.core.presentation.screens.preferences.navigation.Settings
import com.bobbyesp.docucraft.feature.docscanner.navigation.DeleteDocument
import com.bobbyesp.docucraft.feature.docscanner.navigation.DocumentActions
import com.bobbyesp.docucraft.feature.docscanner.navigation.Home
import com.bobbyesp.docucraft.feature.pdfviewer.navigation.PdfViewer
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
        val stack = backStack(Home)

        BackStackNavigator(stack).goTo(document)

        assertEquals(listOf(Home, document), stack.toList())
    }

    /** A double tap on a list item used to open the same document twice. */
    @Test
    fun `going to the destination already on top does nothing`() {
        val stack = backStack(Home, document)

        BackStackNavigator(stack).goTo(document)

        assertEquals(listOf(Home, document), stack.toList())
    }

    @Test
    fun `the same destination is still reachable from somewhere else`() {
        val stack = backStack(Home, document, Settings)

        BackStackNavigator(stack).goTo(document)

        assertEquals(listOf(Home, document, Settings, document), stack.toList())
    }

    @Test
    fun `going back leaves the current destination`() {
        val stack = backStack(Home, document)

        BackStackNavigator(stack).goBack()

        assertEquals(listOf(Home), stack.toList())
    }

    /**
     * `NavDisplay` requires a non-empty back stack and throws on an empty one. The system never
     * asks to pop the root — the back handler is disabled there — but a screen calling `goBack`
     * once too often would otherwise take the app down with it.
     */
    @Test
    fun `going back at the root does nothing rather than emptying the stack`() {
        val stack = backStack(Home)

        BackStackNavigator(stack).goBack()

        assertEquals(listOf(Home), stack.toList())
    }

    /**
     * Deleting a document has to dismiss the confirmation and the menu that opened it, both at
     * once. Counting `goBack` calls would depend on how the user got there.
     */
    @Test
    fun `going back while a condition holds pops the whole group`() {
        val stack = backStack(Home, actions, confirmDelete)

        BackStackNavigator(stack).goBackWhile { it is DocumentActions || it is DeleteDocument }

        assertEquals(listOf(Home), stack.toList())
    }

    @Test
    fun `going back while a condition holds stops at the first destination it does not match`() {
        val stack = backStack(Home, document, actions)

        BackStackNavigator(stack).goBackWhile { it is DocumentActions }

        assertEquals(listOf(Home, document), stack.toList())
    }

    /** Even a predicate that accepts everything must leave the stack standing. */
    @Test
    fun `going back while a condition holds never empties the stack`() {
        val stack = backStack(Home, document, actions)

        BackStackNavigator(stack).goBackWhile { true }

        assertEquals(listOf(Home), stack.toList())
    }

    /**
     * A destination whose subject has ceased to exist is not asking to go back. The viewer used to
     * say `goBack` when its document was deleted, which popped whatever was on top — a settings
     * screen the user had opened above it, say — and left the dead viewer in place underneath.
     */
    @Test
    fun `removing a destination takes it off the stack wherever it sits`() {
        val stack = backStack(Home, document, Settings)

        BackStackNavigator(stack).removeDestination(document)

        assertEquals(listOf(Home, Settings), stack.toList())
    }

    @Test
    fun `removing a destination leaves the rest of the stack alone`() {
        val stack = backStack(Home, document)

        BackStackNavigator(stack).removeDestination(document)

        assertEquals(listOf(Home), stack.toList())
    }

    /** The same document can be reached twice, and a deleted one is gone from both routes. */
    @Test
    fun `removing a destination removes every occurrence of it`() {
        val stack = backStack(Home, document, Settings, document)

        BackStackNavigator(stack).removeDestination(document)

        assertEquals(listOf(Home, Settings), stack.toList())
    }

    @Test
    fun `removing a destination that is not there changes nothing`() {
        val stack = backStack(Home, Settings)

        BackStackNavigator(stack).removeDestination(document)

        assertEquals(listOf(Home, Settings), stack.toList())
    }

    /** Even the root ceasing to exist must leave the stack standing. */
    @Test
    fun `removing the only destination does nothing rather than emptying the stack`() {
        val stack = backStack(document)

        BackStackNavigator(stack).removeDestination(document)

        assertEquals(listOf(document), stack.toList())
    }

    private companion object {
        val document = PdfViewer(documentUuid = "doc-1")
        val actions = DocumentActions(documentUuid = "doc-1")
        val confirmDelete = DeleteDocument(documentUuid = "doc-1")
    }
}
