/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.documentcontent

/**
 * A caret in the document: its page, and its position in that page's [TextSelection.text]. Ordered
 * by page, then by reading order on the page.
 */
data class TextCaret(val page: Int, val offset: Int) : Comparable<TextCaret> {
    override fun compareTo(other: TextCaret): Int =
        compareValuesBy(this, other, TextCaret::page, TextCaret::offset)
}

/**
 * A selection that may run over several pages: the text between caret [start] and caret [end],
 * [start] never after [end]. Every page between them is selected whole.
 *
 * Only the two ends are kept. Each page's share is worked out from its own [TextSelection] when it
 * is needed ([spanOn]), so a selection over many pages costs nothing until it is drawn or copied,
 * and only the pages on screen have to be drawn.
 */
data class DocumentSelection(val start: TextCaret, val end: TextCaret) {
    init {
        require(start <= end) { "A selection cannot end before it starts: $start..$end" }
    }

    /** The pages the selection touches. */
    val pages: IntRange
        get() = start.page..end.page

    /** Whether nothing is selected: both ends at the same caret. */
    val isEmpty: Boolean
        get() = start == end

    /**
     * What is selected of [page], whose text is [length] characters long; `null` when the selection
     * does not reach it or takes nothing from it.
     */
    fun spanOn(page: Int, length: Int): TextSpan? {
        if (page !in pages) return null
        val from = if (page == start.page) start.offset.coerceIn(0, length) else 0
        val to = if (page == end.page) end.offset.coerceIn(0, length) else length
        return if (from < to) TextSpan(from, to) else null
    }

    /**
     * The selected text as it should be pasted: each page's share, pages separated by a line break,
     * without the spaces or line breaks a handle between words may have taken in at either end.
     *
     * @param page The text of each page in [pages], or `null` for one with none (a scanned page),
     *   which is left out. Every page in [pages] is asked for: load them all before copying.
     */
    fun text(page: (index: Int) -> TextSelection?): String =
        pages
            .mapNotNull { index ->
                val selection = page(index) ?: return@mapNotNull null
                spanOn(index, selection.text.length)?.let(selection::text)
            }
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n")
            .trim()

    /** What to highlight on [page], whose text is [selection]. */
    fun highlightRects(page: Int, selection: TextSelection): List<NormalizedRect> =
        spanOn(page, selection.text.length)?.let(selection::highlightRects).orEmpty()

    companion object {
        /** The selection from [anchor] to [focus], in whichever order they were chosen. */
        fun between(anchor: TextCaret, focus: TextCaret): DocumentSelection =
            DocumentSelection(minOf(anchor, focus), maxOf(anchor, focus))

        /**
         * [anchor] grown to reach [focus], never shrunk: the selection a drag makes. After a long
         * press the anchor is the pressed word, which stays selected whichever way the finger goes;
         * for a handle it is the caret at the other end, so this is [between].
         */
        fun extending(anchor: DocumentSelection, focus: TextCaret): DocumentSelection =
            DocumentSelection(minOf(anchor.start, focus), maxOf(anchor.end, focus))
    }
}
