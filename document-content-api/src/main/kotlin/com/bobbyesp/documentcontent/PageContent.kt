/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.documentcontent

/**
 * Where a document lives, as a URI string. Its own type, so this module depends on nothing, not
 * even `:scanner-api`.
 */
@JvmInline value class DocumentSource(val value: String)

/** Where a page's content came from. */
enum class ContentOrigin {
    /** The PDF's own text layer. */
    EMBEDDED,

    /** Read from the page's image by text recognition. */
    RECOGNIZED,
}

/**
 * A word and where it is on the page. Words, not characters, are the common ground: text
 * recognition gives words, and word selection is what readers of scanned documents expect.
 */
data class TextWord(val text: String, val bounds: NormalizedRect)

/** A line of words, in reading order. A blank line has none; it still separates paragraphs. */
data class TextLine(val words: List<TextWord>)

/**
 * The text of a page, lines in reading order.
 *
 * @param confidence How sure recognition is, from 0 to 1; `null` for [ContentOrigin.EMBEDDED].
 */
data class PageText(
    val lines: List<TextLine>,
    val origin: ContentOrigin,
    val confidence: Float? = null,
) {
    /** True when the page has no words: an image-only page, such as a camera scan. */
    val isBlank: Boolean
        get() = lines.all { it.words.isEmpty() }
}

/** An area of the page that leads somewhere. */
sealed interface PageLink {
    /** Where it can be tapped. A link can run over several lines. */
    val bounds: List<NormalizedRect>

    /** Leads out of the document. The URI is as the document has it, unchecked. */
    data class External(override val bounds: List<NormalizedRect>, val uri: String) : PageLink

    /**
     * Leads to another place in the document.
     *
     * @param position Where on that page, when the document says.
     */
    data class Internal(
        override val bounds: List<NormalizedRect>,
        val pageIndex: Int,
        val position: NormalizedPoint?,
    ) : PageLink
}

/**
 * What a provider could make of a page. "No text" and "cannot read" are not failures: the viewer
 * tells the reader differently about each, so they are results of their own rather than errors.
 */
sealed interface PageContentResult {
    /**
     * The page's content.
     *
     * @param text `null` when the page has links but no text, such as a linked image.
     */
    data class Available(val text: PageText?, val links: List<PageLink>) : PageContentResult

    /** An image-only page: there is nothing to select. */
    data object NoText : PageContentResult

    /** This device cannot read the page's content. */
    data object Unsupported : PageContentResult

    data class Failed(val cause: Throwable) : PageContentResult
}
