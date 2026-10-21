/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft

import com.bobbyesp.docucraft.feature.widgets.presentation.ENTRY_ACTIVITY
import org.junit.Assert.assertEquals
import org.junit.Test

class EntryActivityTest {

    /**
     * The widgets open `MainActivity` by name. If the class moved, a press on a widget would open
     * nothing, and only a home screen would show it.
     */
    @Test
    fun `the entry the widgets name is MainActivity`() {
        assertEquals(MainActivity::class.java.name, ENTRY_ACTIVITY)
    }
}
