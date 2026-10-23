/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.domain.usecase

import com.bobbyesp.docucraft.core.domain.model.UserPreferences
import com.bobbyesp.docucraft.core.domain.model.ViewerDefaults
import com.bobbyesp.docucraft.core.domain.model.ViewerDisplaySettings
import com.bobbyesp.docucraft.core.domain.model.ViewerFitMode
import com.bobbyesp.docucraft.core.domain.preferences.SettingsRepository
import com.bobbyesp.docucraft.feature.pdfviewer.data.settings.InMemoryViewerSessionSettings
import com.bobbyesp.docucraft.feature.pdfviewer.domain.model.ViewerDocumentRef
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Decision D2, case by case (`docs/pdf-viewer.md`). What a document opens with is whatever was set
 * for it in this session, otherwise the user's defaults if they turned them on, otherwise the
 * factory settings.
 */
class ViewerDisplaySettingsTest {

    private val preferences = MutableStateFlow(UserPreferences())
    private val settingsRepository: SettingsRepository = mockk {
        every { settings } returns preferences
    }
    private val session = InMemoryViewerSessionSettings()
    private val observe = ObserveViewerDisplaySettingsUseCase(session, settingsRepository)
    private val update = UpdateViewerDisplaySettingsUseCase(session)

    @Test
    fun `with the switch off, a new document opens with the factory settings`() = runTest {
        preferences.value = UserPreferences(viewerDefaults = ViewerDefaults(false, NIGHT_PAGE))

        assertEquals(ViewerDisplaySettings.Factory, observe(DOC_A).first().settings)
    }

    @Test
    fun `the factory settings fit the width, without night mode`() {
        assertEquals(
            ViewerDisplaySettings(ViewerFitMode.WIDTH, nightMode = false),
            ViewerDisplaySettings.Factory,
        )
    }

    @Test
    fun `with the switch on, a new document opens with the defaults`() = runTest {
        preferences.value = UserPreferences(viewerDefaults = ViewerDefaults(true, NIGHT_PAGE))

        assertEquals(NIGHT_PAGE, observe(DOC_A).first().settings)
    }

    @Test
    fun `what was set for a document wins for that document only`() = runTest {
        preferences.value = UserPreferences(viewerDefaults = ViewerDefaults(true, NIGHT_PAGE))

        update(DOC_A, HEIGHT_DAY)

        assertEquals(HEIGHT_DAY, observe(DOC_A).first().settings)
        assertEquals(NIGHT_PAGE, observe(DOC_B).first().settings)
    }

    /** A document not touched this session follows the defaults as they change. */
    @Test
    fun `changing the defaults mid-session spares documents already adjusted`() = runTest {
        preferences.value = UserPreferences(viewerDefaults = ViewerDefaults(true, NIGHT_PAGE))
        update(DOC_A, HEIGHT_DAY)

        preferences.value = UserPreferences(viewerDefaults = ViewerDefaults(true, PROPORTIONAL_DAY))

        assertEquals(HEIGHT_DAY, observe(DOC_A).first().settings)
        assertEquals(PROPORTIONAL_DAY, observe(DOC_B).first().settings)
    }

    @Test
    fun `turning the switch off mid-session also spares them`() = runTest {
        preferences.value = UserPreferences(viewerDefaults = ViewerDefaults(true, NIGHT_PAGE))
        update(DOC_A, HEIGHT_DAY)

        preferences.value = UserPreferences(viewerDefaults = ViewerDefaults(false, NIGHT_PAGE))

        assertEquals(HEIGHT_DAY, observe(DOC_A).first().settings)
        assertEquals(ViewerDisplaySettings.Factory, observe(DOC_B).first().settings)
    }

    /** Only a choice is worth keeping across a process death; defaults are followed, not kept. */
    @Test
    fun `a choice is reported as such, and the defaults are not`() = runTest {
        update(DOC_A, HEIGHT_DAY)

        assertEquals(true, observe(DOC_A).first().isChosen)
        assertEquals(false, observe(DOC_B).first().isChosen)
    }

    /** External documents are known by their location, whatever the providing app calls them. */
    @Test
    fun `an external document is remembered by its location`() = runTest {
        update(ViewerDocumentRef.External(uri = EXTERNAL, displayName = "a.pdf"), HEIGHT_DAY)

        val reopened = ViewerDocumentRef.External(uri = EXTERNAL, displayName = "A copy.pdf")

        assertEquals(HEIGHT_DAY, observe(reopened).first().settings)
    }

    private companion object {
        val DOC_A = ViewerDocumentRef.Catalogued("doc-a")
        val DOC_B = ViewerDocumentRef.Catalogued("doc-b")
        const val EXTERNAL = "content://media/external/downloads/37"

        val NIGHT_PAGE = ViewerDisplaySettings(ViewerFitMode.BOTH, nightMode = true)
        val HEIGHT_DAY = ViewerDisplaySettings(ViewerFitMode.HEIGHT, nightMode = false)
        val PROPORTIONAL_DAY = ViewerDisplaySettings(ViewerFitMode.PROPORTIONAL, nightMode = false)
    }
}
