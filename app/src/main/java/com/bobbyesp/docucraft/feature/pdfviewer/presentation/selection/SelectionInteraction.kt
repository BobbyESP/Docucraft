/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.selection

import com.bobbyesp.documentcontent.DocumentSelection
import com.bobbyesp.documentcontent.NormalizedPoint
import com.bobbyesp.documentcontent.PageContentResult
import com.bobbyesp.documentcontent.TextPosition
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
    /** It landed on a word, which becomes the selection. */
    data class Select(val selection: DocumentSelection) : LongPressOutcome

    /** The page has text, but the press is not on a word: the viewer keeps it. */
    data object NoWord : LongPressOutcome

    /** The page has no text to select, and the reader should know why. */
    data class NoText(val reason: TextUnavailable) : LongPressOutcome

    /** The page's text is not known yet, or could not be read. */
    data object NotReady : LongPressOutcome
}

/**
 * Where a selection's two handles go: under the start of its first word and the end of its last.
 */
data class HandlePositions(val start: PagePoint?, val end: PagePoint?)

/** A point on a page, normalized to it. */
data class PagePoint(val page: Int, val position: NormalizedPoint)

/**
 * What touches do to a selection, given the pages whose text is known. Pure: the screen asks it
 * synchronously (a long press must be claimed or not at once), and the tests ask it without a
 * device.
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
                    val at = TextPosition(page, word)
                    LongPressOutcome.Select(DocumentSelection(at, at))
                }
            }
            PageTextState.NoText -> LongPressOutcome.NoText(TextUnavailable.ImageOnly)
            PageTextState.Unsupported -> LongPressOutcome.NoText(TextUnavailable.UnsupportedDevice)
            PageTextState.Failed,
            null -> LongPressOutcome.NotReady
        }

    /**
     * The selection from [anchor], the end that stays put, to the word nearest [point]: where a
     * dragged handle or finger is. `null` when that page has no text known, so the selection keeps
     * its last shape rather than jumping.
     */
    fun extend(
        anchor: TextPosition,
        pages: Map<Int, PageTextState>,
        page: Int,
        point: NormalizedPoint,
    ): DocumentSelection? {
        val text = pages[page] as? PageTextState.Text ?: return null
        val word = text.selection.nearestWord(point) ?: return null
        return DocumentSelection.between(anchor, TextPosition(page, word))
    }

    fun handles(selection: DocumentSelection, pages: Map<Int, PageTextState>): HandlePositions {
        fun bounds(position: TextPosition) =
            (pages[position.page] as? PageTextState.Text)
                ?.selection
                ?.words
                ?.getOrNull(position.word)
                ?.bounds
        return HandlePositions(
            start =
                bounds(selection.start)?.let {
                    PagePoint(selection.start.page, NormalizedPoint(it.left, it.bottom))
                },
            end =
                bounds(selection.end)?.let {
                    PagePoint(selection.end.page, NormalizedPoint(it.right, it.bottom))
                },
        )
    }

    /** Every word on the pages the selection touches: what *Select all* means here. */
    fun selectAll(
        selection: DocumentSelection,
        lastWordOf: (page: Int) -> Int?,
    ): DocumentSelection {
        val lastPage =
            selection.pages.reversed().firstOrNull { (lastWordOf(it) ?: -1) >= 0 }
                ?: return selection
        val firstPage = selection.pages.first { (lastWordOf(it) ?: -1) >= 0 }
        return DocumentSelection(
            TextPosition(firstPage, 0),
            TextPosition(lastPage, lastWordOf(lastPage)!!),
        )
    }
}
