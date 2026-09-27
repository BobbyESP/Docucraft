/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation

import com.bobbyesp.docucraft.feature.pdfviewer.presentation.selection.HandlePositions
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.selection.LongPressOutcome
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.selection.PagePoint
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.selection.PageTextState
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.selection.SelectionInteraction
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.selection.TextUnavailable
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.selection.toTextState
import com.bobbyesp.documentcontent.ContentOrigin
import com.bobbyesp.documentcontent.DocumentSelection
import com.bobbyesp.documentcontent.NormalizedPoint
import com.bobbyesp.documentcontent.NormalizedRect
import com.bobbyesp.documentcontent.PageContentResult
import com.bobbyesp.documentcontent.PageText
import com.bobbyesp.documentcontent.TextLine
import com.bobbyesp.documentcontent.TextPosition
import com.bobbyesp.documentcontent.TextSelection
import com.bobbyesp.documentcontent.TextWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SelectionInteractionTest {

    // Page 0: "one two" on a line at y 0.10..0.14; page 1: a scan; page 2: "three".
    private val pages: Map<Int, PageTextState> =
        mapOf(
            0 to PageTextState.Text(page(0.10f, "one", "two")),
            1 to PageTextState.NoText,
            2 to PageTextState.Text(page(0.10f, "three")),
        )

    @Test
    fun `a long press on a word selects it`() {
        val outcome = SelectionInteraction.longPress(pages, 0, centreOf(word = 1))

        assertEquals(LongPressOutcome.Select(at(0, 1)..at(0, 1)), outcome)
    }

    @Test
    fun `a long press off the words leaves the press to the viewer`() {
        assertEquals(
            LongPressOutcome.NoWord,
            SelectionInteraction.longPress(pages, 0, NormalizedPoint(0.9f, 0.9f)),
        )
    }

    @Test
    fun `a long press on a page without text says why`() {
        assertEquals(
            LongPressOutcome.NoText(TextUnavailable.ImageOnly),
            SelectionInteraction.longPress(pages, 1, NormalizedPoint(0.5f, 0.5f)),
        )
        assertEquals(
            LongPressOutcome.NoText(TextUnavailable.UnsupportedDevice),
            SelectionInteraction.longPress(
                mapOf(0 to PageTextState.Unsupported),
                0,
                NormalizedPoint(0.5f, 0.5f),
            ),
        )
    }

    @Test
    fun `a long press on a page not read yet is not claimed`() {
        assertEquals(
            LongPressOutcome.NotReady,
            SelectionInteraction.longPress(pages, 7, NormalizedPoint(0.5f, 0.5f)),
        )
    }

    @Test
    fun `dragging extends from the anchor to the nearest word, onto another page`() {
        assertEquals(
            at(0, 1)..at(2, 0),
            SelectionInteraction.extend(at(0, 1), pages, 2, NormalizedPoint(0.9f, 0.5f)),
        )
    }

    @Test
    fun `dragging back past the anchor turns the selection around`() {
        assertEquals(
            at(0, 0)..at(0, 1),
            SelectionInteraction.extend(at(0, 1), pages, 0, NormalizedPoint(0f, 0.12f)),
        )
    }

    @Test
    fun `dragging over a page without text keeps the selection as it was`() {
        assertNull(SelectionInteraction.extend(at(0, 1), pages, 1, NormalizedPoint(0.5f, 0.5f)))
    }

    @Test
    fun `the handles sit under the start of the first word and the end of the last`() {
        val handles = SelectionInteraction.handles(at(0, 0)..at(2, 0), pages)

        assertEquals(
            HandlePositions(
                start = PagePoint(0, NormalizedPoint(0.1f, 0.14f)),
                end = PagePoint(2, NormalizedPoint(0.1f + WordWidth, 0.14f)),
            ),
            handles,
        )
    }

    @Test
    fun `a handle on a page not read has no place yet`() {
        val handles = SelectionInteraction.handles(at(0, 0)..at(5, 0), pages)

        assertNull(handles.end)
    }

    @Test
    fun `select all takes in the whole of the pages the selection touches`() {
        val lastWords = mapOf(0 to 1, 1 to null, 2 to 0)

        assertEquals(
            at(0, 0)..at(2, 0),
            SelectionInteraction.selectAll(at(0, 1)..at(2, 0)) { lastWords[it] },
        )
    }

    @Test
    fun `a page with links but no text has nothing to select`() {
        assertEquals(
            PageTextState.NoText,
            PageContentResult.Available(text = null, links = emptyList()).toTextState(),
        )
    }

    private fun at(page: Int, word: Int) = TextPosition(page, word)

    private operator fun TextPosition.rangeTo(end: TextPosition) = DocumentSelection(this, end)

    private fun centreOf(word: Int): NormalizedPoint {
        val bounds = (pages.getValue(0) as PageTextState.Text).selection.words[word].bounds
        return NormalizedPoint((bounds.left + bounds.right) / 2, (bounds.top + bounds.bottom) / 2)
    }

    private fun page(top: Float, vararg words: String) =
        TextSelection(
            PageText(
                lines =
                    listOf(
                        TextLine(
                            words.mapIndexed { i, text ->
                                val left = 0.1f + i * 0.2f
                                TextWord(
                                    text,
                                    NormalizedRect(left, top, left + WordWidth, top + 0.04f),
                                )
                            }
                        )
                    ),
                origin = ContentOrigin.EMBEDDED,
            )
        )

    private companion object {
        const val WordWidth = 0.15f
    }
}
