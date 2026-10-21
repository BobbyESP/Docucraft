/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderTest {

    // --- names ---

    @Test
    fun `names that differ in case, accents or spaces are the same name`() {
        val expected = "facturas de la luz"

        for (name in
            listOf("Facturas de la luz", "  facturas   de la LUZ ", "Fácturas de la lüz")) {
            assertEquals("for <$name>", expected, normalizedNameOf(name))
        }
    }

    @Test
    fun `names that differ in a letter are different names`() {
        assertFalse(normalizedNameOf("Facturas") == normalizedNameOf("Factura"))
        assertFalse(normalizedNameOf("2025") == normalizedNameOf("2026"))
    }

    @Test
    fun `a name is kept as written, without stray spaces`() {
        assertEquals("Facturas de la Luz", tidyNameOf("  Facturas   de la\tLuz \n"))
        assertEquals("", tidyNameOf("   "))
    }

    // --- the tree ---

    @Test
    fun `a folder cannot go inside itself`() {
        assertTrue(FolderTree.wouldContainItself("a", pathToNewParent = listOf("a")))
    }

    // The loop the database's own rules cannot see: a goes into c, which is inside b, inside a.
    @Test
    fun `a folder cannot go inside a folder that is inside it`() {
        assertTrue(FolderTree.wouldContainItself("a", pathToNewParent = listOf("a", "b", "c")))
        assertTrue(FolderTree.wouldContainItself("b", pathToNewParent = listOf("a", "b", "c")))
    }

    @Test
    fun `a folder can go anywhere else, the root included`() {
        assertFalse(FolderTree.wouldContainItself("a", pathToNewParent = emptyList()))
        assertFalse(FolderTree.wouldContainItself("a", pathToNewParent = listOf("x", "y")))
    }

    // --- a name that is free ---

    @Test
    fun `a name nobody has is kept`() {
        assertEquals("2026", FolderTree.freeName("2026", taken = setOf("2025")))
    }

    @Test
    fun `a name that is taken gets the first number that is free`() {
        assertEquals("2026 (2)", FolderTree.freeName("2026", taken = setOf("2026")))
        assertEquals(
            "2026 (4)",
            FolderTree.freeName("2026", taken = setOf("2026", "2026 (2)", "2026 (3)")),
        )
    }

    @Test
    fun `whether a name is taken does not depend on case or accents`() {
        assertEquals("Años (2)", FolderTree.freeName("Años", taken = setOf("anos")))
    }
}
