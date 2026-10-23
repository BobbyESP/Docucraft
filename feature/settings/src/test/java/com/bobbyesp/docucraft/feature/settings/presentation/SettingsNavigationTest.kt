/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.settings.presentation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.docucraft.core.navigation.BackStackNavigator
import com.bobbyesp.docucraft.core.navigation.keys.AboutSettings
import com.bobbyesp.docucraft.core.navigation.keys.AppearanceSettings
import com.bobbyesp.docucraft.core.navigation.keys.DocumentViewerSettings
import com.bobbyesp.docucraft.core.navigation.keys.PdfViewer
import com.bobbyesp.docucraft.core.navigation.keys.Settings
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What back means from the settings list, which is not the same thing on a phone and on a tablet.
 *
 * On a phone only one settings screen is on screen at a time, so leaving the list is one pop and
 * nothing distinguishes it from `goBack`. On a wide window the list and one of its detail screens
 * are up together, and `goBack` there closes the detail beside the user rather than the settings
 * they asked to leave — a back affordance that appears to do nothing.
 */
/** Stands for Home, the root the settings are opened from, which the settings do not depend on. */
private data object Home : NavKey

class SettingsNavigationTest {

    private fun backStack(vararg keys: NavKey) = NavBackStack(*keys)

    @Test
    fun `leaving settings from the list alone returns to what opened it`() {
        val stack = backStack(Home, Settings)

        BackStackNavigator(stack).leaveSettings()

        assertEquals(listOf(Home), stack.toList())
    }

    /** The case a phone never reaches: the list and its detail are both on screen. */
    @Test
    fun `leaving settings with a detail open leaves the whole area, not just the detail`() {
        val stack = backStack(Home, Settings, AppearanceSettings)

        BackStackNavigator(stack).leaveSettings()

        assertEquals(listOf(Home), stack.toList())
    }

    @Test
    fun `leaving settings works from the document viewer detail too`() {
        val stack = backStack(Home, Settings, DocumentViewerSettings)

        BackStackNavigator(stack).leaveSettings()

        assertEquals(listOf(Home), stack.toList())
    }

    @Test
    fun `leaving settings works from the about detail too`() {
        val stack = backStack(Home, Settings, AboutSettings)

        BackStackNavigator(stack).leaveSettings()

        assertEquals(listOf(Home), stack.toList())
    }

    /** Settings is reachable with a document open beneath it, and that document must survive. */
    @Test
    fun `leaving settings stops at whatever was underneath it`() {
        val stack = backStack(Home, document, Settings, AppearanceSettings)

        BackStackNavigator(stack).leaveSettings()

        assertEquals(listOf(Home, document), stack.toList())
    }

    private companion object {
        val document = PdfViewer(documentUuid = "doc-1")
    }
}
