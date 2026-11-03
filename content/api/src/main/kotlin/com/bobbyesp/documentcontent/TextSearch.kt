/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.documentcontent

import java.text.Normalizer
import java.util.Locale

/**
 * A page's text folded so that a search ignores case, accents, ligatures and how whitespace is
 * broken: `Résumé`, `RESUME` and `résumé` are one word, as in the library's own search.
 *
 * Folding changes the text's length (a ligature becomes two letters, a run of whitespace one
 * space), so each folded character remembers its origin and matches are answered as [TextSpan]s of
 * the *original* text ([TextSelection.text]), which is what highlighting and selection use.
 *
 * Built once per page and searched for every query typed.
 */
class FoldedText
private constructor(
    private val source: String,
    internal val text: String,
    /** The original index of each folded character; `null` when folding moved nothing. */
    private val origin: IntArray?,
) {
    /** Whether the page has no text to search. */
    val isEmpty: Boolean
        get() = text.isBlank()

    /** Where folded character [index] started in the original text. */
    internal fun startOf(index: Int): Int = origin?.get(index) ?: index

    /** Where the original character behind folded character [index] ends, accents included. */
    internal fun endOf(index: Int): Int {
        var end = startOf(index) + 1
        // A character outside the basic plane is two units long and must not be cut in half.
        if (end < source.length && Character.isHighSurrogate(source[end - 1])) end++
        while (end < source.length && source[end].isIgnorable()) end++
        return end
    }

    companion object {
        /** A page with nothing to search. */
        val Empty = FoldedText(source = "", text = "", origin = null)

        /** Folds [original]. */
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
 * A match is a stretch of the page equal to the query once both are folded ([FoldedText]). It never
 * cuts a character, matches never overlap, and a match never spans two pages.
 */
object TextSearch {

    /**
     * The folded form of [query] to pass to [find], or `null` if it is empty or only whitespace.
     */
    fun needle(query: String): String? = FoldedText.of(query.trim()).text.takeIf { it.isNotBlank() }

    /** Every match of [needle] (from [needle]) in [page], in reading order, in original offsets. */
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
 * A character decomposed (`ﬁ` is `f`, `i`), stripped of accents and lower-cased. A ligature gives
 * several characters and a lone accent none, so `e` + accent finds the same as the one-piece `é`.
 */
private fun Char.foldedParts(): CharArray {
    if (isIgnorable()) return NoParts
    val decomposed = Normalizer.normalize(toString(), Normalizer.Form.NFKD)
    val kept = StringBuilder(decomposed.length)
    for (part in decomposed) if (!part.isIgnorable()) kept.append(part)
    return kept.toString().lowercase(Locale.ROOT).toCharArray()
}

private val NoParts = CharArray(0)

/** Accents and invisible format characters (soft hyphen, zero-width space): not part of a word. */
private fun Char.isIgnorable(): Boolean =
    when (Character.getType(this).toByte()) {
        Character.NON_SPACING_MARK,
        Character.COMBINING_SPACING_MARK,
        Character.ENCLOSING_MARK,
        Character.FORMAT -> true

        else -> false
    }
