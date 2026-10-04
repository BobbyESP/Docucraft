/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Fts4QueryTest {

    @Test
    fun `every word becomes a prefix and a space requires them all`() {
        assertEquals("factura* luz*", Fts4Query.of("factura luz"))
    }

    // AND is a word to look for in the query syntax Android's SQLite uses, not an operator.
    @Test
    fun `the words are never joined with AND`() {
        assertEquals("factura* luz* marzo*", Fts4Query.of("  factura   luz\tmarzo "))
    }

    // What the user types is text. Quotes, dashes and asterisks would otherwise open a phrase,
    // exclude a word or break the query.
    @Test
    fun `punctuation separates words and is otherwise dropped`() {
        assertEquals("factura* luz* marzo*", Fts4Query.of("Factura \"luz\" – Marzo*"))
        assertEquals("o* brien* smith*", Fts4Query.of("O'Brien-Smith"))
        assertEquals("42* 10*", Fts4Query.of("42,10 €"))
        assertEquals("title* luz*", Fts4Query.of("title:luz"))
        assertEquals("a* b*", Fts4Query.of("(a) ^b"))
    }

    // OR, NOT and NEAR are operators only in capitals.
    @Test
    fun `operator words are lowered into ordinary words`() {
        assertEquals("luz* or* gas*", Fts4Query.of("luz OR gas"))
        assertEquals("luz* not* gas*", Fts4Query.of("luz NOT gas"))
        assertEquals("luz* near* gas*", Fts4Query.of("luz NEAR gas"))
    }

    // The index's tokenizer removes them, for the query as for the indexed text. Removing them
    // here too would do it by other rules, and break the words where the two disagree.
    @Test
    fun `accents are left for the tokenizer`() {
        assertEquals("canción*", Fts4Query.of("Canción"))
        assertEquals("một* tài* liệu*", Fts4Query.of("Một tài liệu"))
    }

    // Typed as a letter and a combining accent, it is still one letter of one word.
    @Test
    fun `a letter typed with a combining accent stays inside its word`() {
        assertEquals("canción*", Fts4Query.of("Canción"))
    }

    @Test
    fun `an accent on nothing is not a word`() {
        assertNull(Fts4Query.of("́"))
        assertEquals("luz*", Fts4Query.of("́ luz"))
    }

    @Test
    fun `letters of any script are words`() {
        assertEquals("документ* 文書*", Fts4Query.of("Документ 文書"))
    }

    @Test
    fun `only the first eight words are kept`() {
        assertEquals(
            "a* b* c* d* e* f* g* h*",
            Fts4Query.of("a b c d e f g h i j"),
        )
        assertEquals(Fts4Query.MAX_TERMS, Fts4Query.termsOf("a b c d e f g h i j").size)
    }

    @Test
    fun `nothing to search for is no query`() {
        assertNull(Fts4Query.of(""))
        assertNull(Fts4Query.of("   "))
        assertNull(Fts4Query.of("*** \"\" -- ()"))
    }
}
