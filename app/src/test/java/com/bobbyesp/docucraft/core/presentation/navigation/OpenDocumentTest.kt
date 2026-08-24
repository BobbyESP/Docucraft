/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.docucraft.feature.shared.domain.BasicDocument
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Opening a document from the list must replace the one already open rather than stack on top of
 * it. On windows wide enough to keep the list beside the viewer the list stays tappable, so without
 * this, browsing documents grows the back stack without bound and back stops meaning "close the
 * document".
 */
class OpenDocumentTest {

    private fun document(id: String) =
        BasicDocument(uuid = id, filename = "$id.pdf", uri = "content://docs/$id")

    private fun backStack() = NavBackStack<NavKey>(Route.Home)

    @Test
    fun `opening a document from home pushes it onto the stack`() {
        val backStack = backStack()

        backStack.openDocument(document("a"))

        assertEquals(listOf(Route.Home, Route.PdfViewer(document("a"))), backStack.toList())
    }

    @Test
    fun `opening a second document replaces the first instead of stacking`() {
        val backStack = backStack()

        backStack.openDocument(document("a"))
        backStack.openDocument(document("b"))

        assertEquals(listOf(Route.Home, Route.PdfViewer(document("b"))), backStack.toList())
    }

    @Test
    fun `browsing many documents leaves a single viewer entry`() {
        val backStack = backStack()

        listOf("a", "b", "c", "d", "e").forEach { backStack.openDocument(document(it)) }

        assertEquals(2, backStack.size)
        assertEquals(Route.PdfViewer(document("e")), backStack.last())
    }

    @Test
    fun `back from a document returns to home, not to the previously viewed document`() {
        val backStack = backStack()

        backStack.openDocument(document("a"))
        backStack.openDocument(document("b"))
        backStack.removeLastOrNull()

        assertEquals(listOf(Route.Home), backStack.toList())
    }

    @Test
    fun `reopening the document already open changes nothing`() {
        val backStack = backStack()

        backStack.openDocument(document("a"))
        val before = backStack.toList()

        backStack.openDocument(document("a"))

        assertEquals(before, backStack.toList())
    }
}
