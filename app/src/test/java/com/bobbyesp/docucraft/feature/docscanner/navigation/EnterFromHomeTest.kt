/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.docucraft.core.navigation.keys.DocumentSearch
import com.bobbyesp.docucraft.core.navigation.keys.PdfViewer
import com.bobbyesp.docucraft.core.navigation.keys.Settings
import com.bobbyesp.docucraft.core.presentation.navigation.BackStackNavigator
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A widget is pressed with the app left wherever the user last was. What it opens has to end up
 * directly above the library, so that back leads there and not to whatever was open before.
 */
class EnterFromHomeTest {

    private fun backStack(vararg keys: NavKey) = NavBackStack(*keys)

    @Test
    fun `from the library it goes straight there`() {
        val stack = backStack(Home)

        BackStackNavigator(stack).enterFromHome(PdfViewer("a"))

        assertEquals(listOf(Home, PdfViewer("a")), stack.toList())
    }

    @Test
    fun `whatever was open is left behind`() {
        val stack = backStack(Home, PdfViewer("old"), Settings)

        BackStackNavigator(stack).enterFromHome(PdfViewer("a"))

        assertEquals(listOf(Home, PdfViewer("a")), stack.toList())
    }

    @Test
    fun `the search opens above the library too`() {
        val stack = backStack(Home, PdfViewer("old"))

        BackStackNavigator(stack).enterFromHome(DocumentSearch)

        assertEquals(listOf(Home, DocumentSearch), stack.toList())
    }
}
