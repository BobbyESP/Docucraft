/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.navigation

import androidx.navigation3.runtime.NavKey
import com.bobbyesp.docucraft.core.presentation.screens.preferences.navigation.Settings
import com.bobbyesp.docucraft.feature.docscanner.navigation.DeleteDocument
import com.bobbyesp.docucraft.feature.docscanner.navigation.DocumentActions
import com.bobbyesp.docucraft.feature.docscanner.navigation.Home
import com.bobbyesp.docucraft.feature.pdfviewer.navigation.PdfViewer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which document the list marks as selected, read off the back stack.
 *
 * Worth pinning because the obvious reading of the stack is the wrong one. "The document that is
 * open" is not "the top of the stack": an overlay sits *above* the layout without replacing it, so
 * a sheet or a dialog leaves the viewer on screen underneath it.
 */
class OpenDocumentHighlightTest {

    @Test
    fun `nothing is selected with no document open`() {
        assertNull(stack(Home).openDocumentId())
    }

    @Test
    fun `an open document is selected`() {
        assertEquals("doc-1", stack(Home, document).openDocumentId())
    }

    /** The bug: the sheet is *about* that document, and it stopped being highlighted. */
    @Test
    fun `an overlay over the viewer does not clear the selection`() {
        val backStack = stack(Home, document, DocumentActions(documentUuid = "doc-1"))

        assertEquals("doc-1", backStack.openDocumentId())
    }

    @Test
    fun `an overlay stacked on another overlay does not clear it either`() {
        val backStack =
            stack(
                Home,
                document,
                DocumentActions(documentUuid = "doc-1"),
                DeleteDocument(documentUuid = "doc-1"),
            )

        assertEquals("doc-1", backStack.openDocumentId())
    }

    /**
     * Settings takes the document list off screen with it, so this highlight is one nobody can see
     * — and it has to be right again the moment the list animates back in.
     */
    @Test
    fun `a destination that replaces the layout leaves the selection standing`() {
        assertEquals("doc-1", stack(Home, document, Settings).openDocumentId())
    }

    @Test
    fun `the most recently opened document wins`() {
        val backStack = stack(Home, document, PdfViewer(documentUuid = "doc-2"))

        assertEquals("doc-2", backStack.openDocumentId())
    }

    /** Acting on a document from the list is not the same as having it open. */
    @Test
    fun `acting on a document without opening it selects nothing`() {
        assertNull(stack(Home, DocumentActions(documentUuid = "doc-1")).openDocumentId())
    }

    private fun stack(vararg keys: NavKey): List<NavKey> = keys.toList()

    private companion object {
        val document = PdfViewer(documentUuid = "doc-1")
    }
}
