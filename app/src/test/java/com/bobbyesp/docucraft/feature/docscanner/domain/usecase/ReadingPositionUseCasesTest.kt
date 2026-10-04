/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.core.domain.model.UserPreferences
import com.bobbyesp.docucraft.core.domain.preferences.SettingsRepository
import com.bobbyesp.docucraft.feature.docscanner.FakeDocumentActivityRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ReadingPosition
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whether the app remembers where documents were left is the user's choice. These are the rules
 * that choice has: what is read and written while it is on, and what turning it off undoes.
 */
class ReadingPositionUseCasesTest {

    private val preferences = MutableStateFlow(UserPreferences())
    private val settings: SettingsRepository = mockk {
        every { settings } returns preferences
        coEvery { setRememberReadingPosition(any()) } answers
            {
                preferences.update { it.copy(rememberReadingPosition = firstArg()) }
            }
    }
    private val activity = FakeDocumentActivityRepository()

    private val get = GetReadingPositionUseCase(settings, activity)
    private val remember = RememberReadingPositionUseCase(settings, activity)
    private val setMemory = SetReadingPositionMemoryUseCase(settings, activity)

    @Test
    fun `it is remembered unless the user says otherwise`() {
        assertTrue(UserPreferences().rememberReadingPosition)
    }

    @Test
    fun `a document opens where it was left`() = runTest {
        remember("doc-1", ReadingPosition(7, 0.25f))

        assertEquals(ReadingPosition(7, 0.25f), get("doc-1"))
        assertNull(get("another"))
    }

    @Test
    fun `while it is off nothing is written down`() = runTest {
        setMemory(false)

        remember("doc-1", ReadingPosition(7, 0.25f))

        assertTrue(activity.positions.isEmpty())
    }

    /** Off means the app does not remember, not that it stops adding to what it remembers. */
    @Test
    fun `turning it off forgets what was remembered`() = runTest {
        remember("doc-1", ReadingPosition(7, 0.25f))
        remember("doc-2", ReadingPosition(1, 0f))

        setMemory(false)

        assertTrue(activity.positions.isEmpty())
        assertNull(get("doc-1"))
    }

    @Test
    fun `turning it back on starts from nothing`() = runTest {
        remember("doc-1", ReadingPosition(7, 0.25f))
        setMemory(false)

        setMemory(true)

        assertNull(get("doc-1"))
        remember("doc-1", ReadingPosition(2, 0.5f))
        assertEquals(ReadingPosition(2, 0.5f), get("doc-1"))
    }

    @Test
    fun `turning it on forgets nothing`() = runTest {
        remember("doc-1", ReadingPosition(7, 0.25f))

        setMemory(true)

        assertEquals(ReadingPosition(7, 0.25f), get("doc-1"))
    }
}
