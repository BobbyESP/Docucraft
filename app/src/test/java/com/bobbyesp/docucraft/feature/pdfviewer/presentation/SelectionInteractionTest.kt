/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation

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
import com.bobbyesp.documentcontent.TextCaret
import com.bobbyesp.documentcontent.TextLine
import com.bobbyesp.documentcontent.TextSelection
import com.bobbyesp.documentcontent.TextWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Drive-like behaviour: a long press selects a word, and the ends then move by character. */
class SelectionInteractionTest {

    // Monospaced, 0.02 a character. Page 0: "one two" (text "one two"); page 1: a scan;
    // page 2: "three".
    private val pages: Map<Int, PageTextState> =
        mapOf(
            0 to PageTextState.Text(page("one", "two")),
            1 to PageTextState.NoText,
            2 to PageTextState.Text(page("three")),
        )

    @Test
    fun `a long press on a word selects the whole word`() {
        val outcome = SelectionInteraction.longPress(pages, 0, NormalizedPoint(0.20f, 0.12f))

        assertEquals(LongPressOutcome.Select(at(0, 4)..at(0, 7)), outcome) // "two"
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
    fun `dragging on from a long press moves by character and keeps the pressed word`() {
        val two = at(0, 4)..at(0, 7)

        // Forwards onto page 2, between "th" and "ree": x 0.14 is the t|h|r... boundary after "th".
        assertEquals(
            at(0, 4)..at(2, 2),
            SelectionInteraction.extend(two, pages, 2, NormalizedPoint(0.14f, 0.12f)),
        )
        // Backwards into "one", between "o" and "ne": the word stays selected to its end.
        assertEquals(
            at(0, 1)..at(0, 7),
            SelectionInteraction.extend(two, pages, 0, NormalizedPoint(0.12f, 0.12f)),
        )
    }

    @Test
    fun `a handle drags by character, and past the other end the selection turns around`() {
        // The end handle is dragged; the start, caret 4, stays.
        val fixedStart = at(0, 4)..at(0, 4)

        assertEquals(
            at(0, 4)..at(0, 6),
            SelectionInteraction.extend(fixedStart, pages, 0, NormalizedPoint(0.22f, 0.12f)),
        )
        assertEquals(
            at(0, 1)..at(0, 4),
            SelectionInteraction.extend(fixedStart, pages, 0, NormalizedPoint(0.12f, 0.12f)),
        )
    }

    @Test
    fun `dragging onto the other end selects nothing, so the selection stays as it was`() {
        val fixedStart = at(0, 4)..at(0, 4)
        assertNull(SelectionInteraction.extend(fixedStart, pages, 0, NormalizedPoint(0.18f, 0.12f)))
    }

    @Test
    fun `dragging over a page without text keeps the selection as it was`() {
        assertNull(
            SelectionInteraction.extend(
                at(0, 4)..at(0, 7),
                pages,
                1,
                NormalizedPoint(0.5f, 0.5f),
            )
        )
    }

    @Test
    fun `the handles sit at character edges, at the bottom of the line`() {
        // From "ne" of "one" to "th" of "three".
        val handles = SelectionInteraction.handles(at(0, 1)..at(2, 2), pages)

        assertEquals(PagePoint(0, NormalizedPoint(0.12f, 0.14f)), handles.start?.rounded())
        assertEquals(PagePoint(2, NormalizedPoint(0.14f, 0.14f)), handles.end?.rounded())
    }

    @Test
    fun `a handle on a page not read has no place yet`() {
        assertNull(SelectionInteraction.handles(at(0, 0)..at(5, 0), pages).end)
    }

    @Test
    fun `select all takes in the whole of the pages the selection touches`() {
        val lengths = mapOf(0 to 7, 1 to null, 2 to 5)

        assertEquals(
            at(0, 0)..at(2, 5),
            SelectionInteraction.selectAll(at(0, 2)..at(2, 1)) { lengths[it] },
        )
    }

    @Test
    fun `a page with links but no text has nothing to select`() {
        assertEquals(
            PageTextState.NoText,
            PageContentResult.Available(text = null, links = emptyList()).toTextState(),
        )
    }

    private fun at(page: Int, offset: Int) = TextCaret(page, offset)

    private operator fun TextCaret.rangeTo(end: TextCaret) = DocumentSelection(this, end)

    private fun PagePoint.rounded() =
        copy(
            position =
                NormalizedPoint(
                    Math.round(position.x * 1000) / 1000f,
                    Math.round(position.y * 1000) / 1000f,
                )
        )

    /** Words from x 0.10, 0.02 a character, a character's width apart, on y 0.10..0.14. */
    private fun page(vararg words: String): TextSelection {
        var left = 0.10f
        return TextSelection(
            PageText(
                lines =
                    listOf(
                        TextLine(
                            words.map { text ->
                                val right = left + text.length * 0.02f
                                TextWord(text, NormalizedRect(left, 0.10f, right, 0.14f)).also {
                                    left = right + 0.02f
                                }
                            }
                        )
                    ),
                origin = ContentOrigin.EMBEDDED,
            )
        )
    }
}
