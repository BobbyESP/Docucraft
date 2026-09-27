/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.selection

import com.bobbyesp.documentcontent.DocumentSelection
import com.bobbyesp.documentcontent.NormalizedPoint
import com.bobbyesp.documentcontent.PageContentResult
import com.bobbyesp.documentcontent.TextCaret
import com.bobbyesp.documentcontent.TextSelection

/** What is known about a page's text, as far as selecting it goes. */
sealed interface PageTextState {
    data class Text(val selection: TextSelection) : PageTextState

    /** An image-only page, such as a scan: nothing to select. */
    data object NoText : PageTextState

    /** This device cannot read the page's text (D1). */
    data object Unsupported : PageTextState

    data object Failed : PageTextState
}

fun PageContentResult.toTextState(): PageTextState =
    when (this) {
        is PageContentResult.Available ->
            text?.let { PageTextState.Text(TextSelection(it)) } ?: PageTextState.NoText
        PageContentResult.NoText -> PageTextState.NoText
        PageContentResult.Unsupported -> PageTextState.Unsupported
        is PageContentResult.Failed -> PageTextState.Failed
    }

/** Why a page has nothing to select, which is what the reader is told. */
enum class TextUnavailable {
    /** The page is an image, such as a scan. */
    ImageOnly,

    /** The device cannot read text from PDFs (D1). */
    UnsupportedDevice,
}

/** What a long press on a page does. */
sealed interface LongPressOutcome {
    /**
     * It landed on a word, which becomes the selection, and stays selected while the finger drags
     * on from it.
     */
    data class Select(val selection: DocumentSelection) : LongPressOutcome

    /** The page has text, but the press is not on a word: the viewer keeps it. */
    data object NoWord : LongPressOutcome

    /** The page has no text to select, and the reader should know why. */
    data class NoText(val reason: TextUnavailable) : LongPressOutcome

    /** The page's text is not known yet, or could not be read. */
    data object NotReady : LongPressOutcome
}

/** Where a selection's two handles go. */
data class HandlePositions(val start: PagePoint?, val end: PagePoint?)

/** A point on a page, normalized to it. */
data class PagePoint(val page: Int, val position: NormalizedPoint)

/**
 * What touches do to a selection, given the pages whose text is known. It behaves like the
 * reference viewers (Google Drive's among them): a long press selects the word under the finger,
 * and from then on the ends move character by character, whether the same finger drags on or a
 * handle is dragged later.
 *
 * Pure: the screen asks it synchronously (a long press must be claimed or not at once), and the
 * tests ask it without a device.
 */
object SelectionInteraction {

    fun longPress(
        pages: Map<Int, PageTextState>,
        page: Int,
        point: NormalizedPoint,
    ): LongPressOutcome =
        when (val text = pages[page]) {
            is PageTextState.Text -> {
                val word = text.selection.wordAt(point)
                if (word == null) {
                    LongPressOutcome.NoWord
                } else {
                    LongPressOutcome.Select(
                        DocumentSelection(TextCaret(page, word.start), TextCaret(page, word.end))
                    )
                }
            }
            PageTextState.NoText -> LongPressOutcome.NoText(TextUnavailable.ImageOnly)
            PageTextState.Unsupported -> LongPressOutcome.NoText(TextUnavailable.UnsupportedDevice)
            PageTextState.Failed,
            null -> LongPressOutcome.NotReady
        }

    /**
     * [anchor] grown to the caret nearest [point]: where a dragged finger or handle is. The anchor
     * is the word a long press chose, or the caret at the end a handle does not move. `null` when
     * that page has no text known, or nothing would be selected, so the selection keeps its last
     * shape rather than jumping or vanishing.
     */
    fun extend(
        anchor: DocumentSelection,
        pages: Map<Int, PageTextState>,
        page: Int,
        point: NormalizedPoint,
    ): DocumentSelection? {
        val text = pages[page] as? PageTextState.Text ?: return null
        val caret = text.selection.caretAt(point) ?: return null
        return DocumentSelection.extending(anchor, TextCaret(page, caret)).takeUnless { it.isEmpty }
    }

    fun handles(selection: DocumentSelection, pages: Map<Int, PageTextState>): HandlePositions {
        fun text(page: Int) = (pages[page] as? PageTextState.Text)?.selection
        return HandlePositions(
            start =
                text(selection.start.page)?.startHandle(selection.start.offset)?.let {
                    PagePoint(selection.start.page, it)
                },
            end =
                text(selection.end.page)?.endHandle(selection.end.offset)?.let {
                    PagePoint(selection.end.page, it)
                },
        )
    }

    /**
     * Every word on the pages the selection touches: what *Select all* means here.
     *
     * @param lengthOf The length of a page's text; `null` or `0` for a page without text.
     */
    fun selectAll(selection: DocumentSelection, lengthOf: (page: Int) -> Int?): DocumentSelection {
        val withText = selection.pages.filter { (lengthOf(it) ?: 0) > 0 }
        if (withText.isEmpty()) return selection
        return DocumentSelection(
            TextCaret(withText.first(), 0),
            TextCaret(withText.last(), lengthOf(withText.last())!!),
        )
    }
}
