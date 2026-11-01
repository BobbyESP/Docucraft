/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.documentcontent

import java.text.Normalizer
import java.util.Locale

/**
 * A page's text, folded so that a search ignores what a reader does not mean by a difference: case,
 * accents, ligatures, and how whitespace happens to be broken (a line, a paragraph, a no-break
 * space). `Résumé`, `RESUME` and `résumé` are one word here, as they are in the library's own
 * search.
 *
 * Folding changes the length of the text (a ligature becomes two letters, a run of whitespace one
 * space), so it remembers where each folded character came from and answers in the *original*
 * text's offsets. A match found here is a [TextSpan] of [TextSelection.text], which is what
 * highlighting and the selection speak.
 *
 * Built once for a page, then searched for as many queries as the reader types: it is the only
 * thing worth keeping of a page once its geometry has been let go of.
 */
class FoldedText
private constructor(
    private val source: String,
    internal val text: String,
    /** The original index of each folded character; `null` when folding moved nothing. */
    private val origin: IntArray?,
) {
    /** Whether there is anything to find in: a page of blank text has no words. */
    val isEmpty: Boolean
        get() = text.isBlank()

    /** Where folded character [index] started in the original text. */
    internal fun startOf(index: Int): Int = origin?.get(index) ?: index

    /**
     * Where the original character that folded character [index] came from ends: after the accents
     * that sit on it, so that a match never ends between a letter and its accent.
     */
    internal fun endOf(index: Int): Int {
        var end = startOf(index) + 1
        // A character outside the basic plane is two units long, and is never cut in half.
        if (end < source.length && Character.isHighSurrogate(source[end - 1])) end++
        while (end < source.length && source[end].isIgnorable()) end++
        return end
    }

    companion object {
        /** A page with nothing to search. */
        val Empty = FoldedText(source = "", text = "", origin = null)

        /** [original] folded; see the class documentation. */
        fun of(original: String): FoldedText {
            if (original.isEmpty()) return Empty
            val folded = StringBuilder(original.length)
            val origins = IntArray(original.length * MaxExpansion)
            var shifted = false

            fun emit(char: Char, from: Int) {
                if (folded.length != from) shifted = true
                origins[folded.length] = from
                folded.append(char)
            }

            var index = 0
            var lastWasSpace = false
            while (index < original.length) {
                val char = original[index]
                when {
                    char.isSeparator() -> {
                        if (!lastWasSpace) emit(' ', index)
                        lastWasSpace = true
                    }

                    char.code < AsciiLimit -> {
                        emit(char.lowercaseChar(), index)
                        lastWasSpace = false
                    }

                    else -> {
                        for (part in char.foldedParts()) emit(part, index)
                        lastWasSpace = false
                    }
                }
                index++
            }
            return FoldedText(
                source = original,
                text = folded.toString(),
                origin = if (shifted) origins.copyOf(folded.length) else null,
            )
        }

        /** Letters per source character, at most: NFKD expands a ligature to a handful. */
        private const val MaxExpansion = 4
        private const val AsciiLimit = 0x80
    }
}

/**
 * Searching a page's text.
 *
 * A match is a stretch of the page's text equal to what was typed once both are folded
 * ([FoldedText]); it is never cut inside a character, and two matches never overlap. Matches do not
 * run from one page to the next: a reader looks for a phrase, and a phrase that a page break splits
 * is not on a page.
 */
object TextSearch {

    /**
     * What to look for, folded the way a page is, or `null` when there is nothing to look for: an
     * empty query, or one of only whitespace.
     */
    fun needle(query: String): String? = FoldedText.of(query.trim()).text.takeIf { it.isNotBlank() }

    /**
     * Every match of [needle] (from [needle]) in [page], in reading order, as spans of the original
     * text.
     */
    fun find(page: FoldedText, needle: String): List<TextSpan> {
        if (needle.isEmpty() || page.isEmpty) return emptyList()
        var found: ArrayList<TextSpan>? = null
        var from = page.text.indexOf(needle)
        while (from >= 0) {
            val last = from + needle.length - 1
            val span = TextSpan(page.startOf(from), page.endOf(last))
            (found ?: ArrayList<TextSpan>().also { found = it }) += span
            from = page.text.indexOf(needle, from + needle.length)
        }
        return found ?: emptyList()
    }
}

/** Whitespace of any kind, the no-break spaces PDFs are full of included. */
private fun Char.isSeparator(): Boolean = isWhitespace() || Character.isSpaceChar(this)

/**
 * A character folded: decomposed (`é` is `e` and an accent, `ﬁ` is `f` and `i`), stripped of its
 * accents, and in lower case. Usually one character; a ligature gives more, and a lone accent gives
 * none, so that `é` typed as a letter and an accent finds the same as the one-piece `é`.
 */
private fun Char.foldedParts(): CharArray {
    if (isIgnorable()) return NoParts
    val decomposed = Normalizer.normalize(toString(), Normalizer.Form.NFKD)
    val kept = StringBuilder(decomposed.length)
    for (part in decomposed) if (!part.isIgnorable()) kept.append(part)
    return kept.toString().lowercase(Locale.ROOT).toCharArray()
}

private val NoParts = CharArray(0)

/**
 * What a reader does not see as a letter: the accents that sit on the letter before them, and the
 * invisible format characters a PDF puts between letters (the soft hyphen of a broken word, a
 * zero-width space). Neither is part of a word to search for.
 */
private fun Char.isIgnorable(): Boolean =
    when (Character.getType(this).toByte()) {
        Character.NON_SPACING_MARK,
        Character.COMBINING_SPACING_MARK,
        Character.ENCLOSING_MARK,
        Character.FORMAT -> true

        else -> false
    }
