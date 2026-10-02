/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.search

import java.text.Normalizer

/**
 * Turns what the user typed into an FTS4 `MATCH` expression that means "every one of these words,
 * each as the beginning of a word", and nothing else.
 *
 * What the user types is text, never syntax. FTS4 gives a meaning to quotes, `*`, `-`, `:`,
 * parentheses and the words `OR`, `NOT` and `NEAR`; passed through, they change what a query means
 * or make it fail to parse. So the input is cut into words the way the index's tokenizer cuts a
 * document, and only those words reach SQLite.
 */
internal object Fts4Query {

    /** More words than this narrow a search no further, and each costs a lookup. */
    const val MAX_TERMS = 8

    /**
     * @return The expression, such as `factura* luz* marzo*`, or `null` when [input] has nothing to
     *   search for.
     */
    fun of(input: String): String? {
        val terms = termsOf(input)
        if (terms.isEmpty()) return null
        // A space between terms requires them all. The word AND is not an operator in the query
        // syntax Android's SQLite is compiled with: it would be one more term to look for.
        return terms.joinToString(" ") { "$it*" }
    }

    /**
     * The words of [input], in lower case.
     *
     * Lower case, because `OR`, `NOT` and `NEAR` are only operators in capitals. Accents are left
     * as they are: SQLite passes each term through the index's own tokenizer, which removes them
     * exactly as it did for the indexed text. Removing them here as well would do it by other
     * rules, and where the two disagree, as on Vietnamese letters with two marks, the term would
     * stop matching the word it was copied from.
     */
    fun termsOf(input: String): List<String> {
        // Composed first, so that a letter typed as a base and a combining accent is one letter.
        val text = Normalizer.normalize(input, Normalizer.Form.NFC).lowercase()

        val terms = ArrayList<String>(MAX_TERMS)
        val term = StringBuilder()
        var index = 0
        while (index < text.length && terms.size < MAX_TERMS) {
            val codePoint = text.codePointAt(index)
            when {
                Character.isLetterOrDigit(codePoint) -> term.appendCodePoint(codePoint)
                // A mark sits on the letter before it. On its own it is no word, and starts none.
                isMark(codePoint) && term.isNotEmpty() -> term.appendCodePoint(codePoint)
                term.isNotEmpty() -> {
                    terms += term.toString()
                    term.clear()
                }
            }
            index += Character.charCount(codePoint)
        }
        if (term.isNotEmpty() && terms.size < MAX_TERMS) terms += term.toString()
        return terms
    }

    /** The accents and other marks that combine with the letter before them. */
    private fun isMark(codePoint: Int): Boolean =
        when (Character.getType(codePoint).toByte()) {
            Character.NON_SPACING_MARK,
            Character.COMBINING_SPACING_MARK,
            Character.ENCLOSING_MARK -> true
            else -> false
        }
}
