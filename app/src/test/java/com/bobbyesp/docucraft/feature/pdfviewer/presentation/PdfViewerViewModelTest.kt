/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation

import androidx.lifecycle.SavedStateHandle
import com.bobbyesp.docucraft.core.domain.StringProvider
import com.bobbyesp.docucraft.core.domain.analytics.AnalyticsEvent
import com.bobbyesp.docucraft.core.domain.model.UserPreferences
import com.bobbyesp.docucraft.core.domain.model.ViewerDisplaySettings
import com.bobbyesp.docucraft.core.domain.model.ViewerFitMode
import com.bobbyesp.docucraft.core.domain.preferences.SettingsRepository
import com.bobbyesp.docucraft.core.domain.repository.AnalyticsHelper
import com.bobbyesp.docucraft.core.util.events.UiEvent
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.sharing.DocumentSharer
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveDocumentUseCase
import com.bobbyesp.docucraft.feature.pdfviewer.data.settings.InMemoryViewerSessionSettings
import com.bobbyesp.docucraft.feature.pdfviewer.domain.actions.DocumentOpener
import com.bobbyesp.docucraft.feature.pdfviewer.domain.model.ViewerDocumentRef
import com.bobbyesp.docucraft.feature.pdfviewer.domain.usecase.ObserveViewerDisplaySettingsUseCase
import com.bobbyesp.docucraft.feature.pdfviewer.domain.usecase.ObserveViewerDocumentUseCase
import com.bobbyesp.docucraft.feature.pdfviewer.domain.usecase.UpdateViewerDisplaySettingsUseCase
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.contract.PdfViewerEffect
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.contract.PdfViewerIntent
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.contract.ViewerDocumentState
import com.bobbyesp.scanner.ContentRef
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PdfViewerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val catalogue = MutableStateFlow<ScannedDocument?>(null)
    private val observeDocument: ObserveDocumentUseCase = mockk {
        every { this@mockk.invoke(UUID) } returns catalogue
    }
    private val sharer: DocumentSharer = mockk(relaxed = true)
    private val opener: DocumentOpener = mockk(relaxed = true)
    private val stringProvider: StringProvider = mockk {
        every { get(any(), *anyVararg()) } returns "message"
    }
    private val analytics = RecordingAnalytics()
    private val preferences = MutableStateFlow(UserPreferences())
    private val settingsRepository: SettingsRepository = mockk {
        every { settings } returns preferences
    }
    private var session = InMemoryViewerSessionSettings()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---------------------------------------------------------------------------------- document

    @Test
    fun aCataloguedDocumentIsLoadingUntilTheCatalogueAnswers() = runTest {
        val viewModel = viewModel(ViewerDocumentRef.Catalogued(UUID))

        assertEquals(ViewerDocumentState.Loading, viewModel.state.value.document)

        catalogue.value = scanned()
        advanceUntilIdle()

        val open = viewModel.state.value.document as ViewerDocumentState.Open
        assertEquals("Invoice", open.document.title)
    }

    /** Waiting and leaving are different answers; see B3 in the navigation audit. */
    @Test
    fun aDeletedDocumentIsGoneRatherThanLoading() = runTest {
        catalogue.value = scanned()
        val viewModel = viewModel(ViewerDocumentRef.Catalogued(UUID))
        advanceUntilIdle()

        catalogue.value = null
        advanceUntilIdle()

        assertEquals(ViewerDocumentState.Gone, viewModel.state.value.document)
    }

    @Test
    fun anExternalDocumentOpensStraightAway() = runTest {
        val viewModel = viewModel(ViewerDocumentRef.External(uri = EXTERNAL, displayName = "a.pdf"))
        advanceUntilIdle()

        val open = viewModel.state.value.document as ViewerDocumentState.Open
        assertEquals("a.pdf", open.document.title)
        assertEquals(EXTERNAL, open.document.uri)
    }

    // --------------------------------------------------------------------------------- settings

    @Test
    fun changingTheFitModeIsKeptAndReported() = runTest {
        val viewModel = viewModel(ViewerDocumentRef.Catalogued(UUID))
        advanceUntilIdle()

        viewModel.onSendIntent(PdfViewerIntent.SetFitMode(ViewerFitMode.WIDTH))

        assertEquals(ViewerFitMode.WIDTH, viewModel.state.value.display?.fitMode)
        assertEquals("fit_mode" to "WIDTH", analytics.settingChanges.single())
    }

    @Test
    fun togglingNightModeReportsTheNewValue() = runTest {
        val viewModel = viewModel(ViewerDocumentRef.Catalogued(UUID))
        advanceUntilIdle()

        viewModel.onSendIntent(PdfViewerIntent.ToggleNightMode)

        assertTrue(viewModel.state.value.display!!.nightMode)
        assertEquals("night_mode" to "true", analytics.settingChanges.single())
    }

    /** Showing the document before its settings are known would lay it out twice. */
    @Test
    fun theDocumentIsOnlyReadyOnceItsSettingsAreKnown() = runTest {
        catalogue.value = scanned()
        val viewModel = viewModel(ViewerDocumentRef.Catalogued(UUID))

        assertEquals(null, viewModel.state.value.readyDocument)

        advanceUntilIdle()

        assertEquals(ViewerDisplaySettings.Factory, viewModel.state.value.display)
        assertEquals("Invoice", viewModel.state.value.readyDocument?.title)
    }

    /** D2: the same document opened again in the same session keeps what the user chose. */
    @Test
    fun aChangeIsRememberedForTheDocumentForTheSession() = runTest {
        val first = viewModel(ViewerDocumentRef.Catalogued(UUID))
        advanceUntilIdle()
        first.onSendIntent(PdfViewerIntent.SetFitMode(ViewerFitMode.HEIGHT))

        val reopened = viewModel(ViewerDocumentRef.Catalogued(UUID))
        advanceUntilIdle()

        assertEquals(ViewerFitMode.HEIGHT, reopened.state.value.display?.fitMode)
    }

    @Test
    fun aChangeIsAlsoKeptInTheEntrysSavedState() = runTest {
        val handle = SavedStateHandle()
        val viewModel = viewModel(ViewerDocumentRef.Catalogued(UUID), handle)
        advanceUntilIdle()

        viewModel.onSendIntent(PdfViewerIntent.ToggleNightMode)
        advanceUntilIdle()

        assertEquals(true, handle.get<Boolean>("viewer_night_mode"))
        assertEquals("WIDTH", handle.get<String>("viewer_fit_mode"))
    }

    /**
     * Found on the emulator: the document was changed in one entry, closed, and reopened in a new
     * one, which showed the choice but had nothing in its own saved state — so a process death
     * brought the defaults back.
     */
    @Test
    fun aReopenedDocumentKeepsTheSessionsChoiceInItsOwnSavedState() = runTest {
        val first = viewModel(ViewerDocumentRef.Catalogued(UUID))
        advanceUntilIdle()
        first.onSendIntent(PdfViewerIntent.ToggleNightMode)
        advanceUntilIdle()

        val handle = SavedStateHandle()
        viewModel(ViewerDocumentRef.Catalogued(UUID), handle)
        advanceUntilIdle()

        assertEquals(true, handle.get<Boolean>("viewer_night_mode"))
    }

    /** A document left alone follows the defaults, so there is nothing of its own to keep. */
    @Test
    fun anUntouchedDocumentKeepsNothingInItsSavedState() = runTest {
        val handle = SavedStateHandle()
        viewModel(ViewerDocumentRef.Catalogued(UUID), handle)
        advanceUntilIdle()

        assertEquals(null, handle.get<Boolean>("viewer_night_mode"))
    }

    /**
     * A1: after a process death the session memory is empty, but the restored entry still knows
     * what its document was showing, and that counts as the same session.
     */
    @Test
    fun afterAProcessDeathTheRestoredEntryKeepsItsSettings() = runTest {
        session = InMemoryViewerSessionSettings()
        val restored =
            SavedStateHandle(
                mapOf("viewer_fit_mode" to "PROPORTIONAL", "viewer_night_mode" to true)
            )

        val viewModel = viewModel(ViewerDocumentRef.Catalogued(UUID), restored)
        advanceUntilIdle()

        assertEquals(
            ViewerDisplaySettings(ViewerFitMode.PROPORTIONAL, nightMode = true),
            viewModel.state.value.display,
        )
    }

    // ---------------------------------------------------------------------------------- actions

    @Test
    fun shareHandsTheDocumentsLocationToTheSharer() = runTest {
        catalogue.value = scanned()
        val viewModel = viewModel(ViewerDocumentRef.Catalogued(UUID))
        advanceUntilIdle()

        viewModel.onSendIntent(PdfViewerIntent.Share)
        viewModel.onSendIntent(PdfViewerIntent.OpenWith)

        verify { sharer.share(ContentRef(LOCATION)) }
        verify { opener.openWith(ContentRef(LOCATION)) }
    }

    /** A legacy file:// document is not re-exposed through the app's own provider. */
    @Test
    fun aDocumentThatCannotLeaveTheAppIsNotHandedOff() = runTest {
        val viewModel =
            viewModel(
                ViewerDocumentRef.External(uri = "file:///sdcard/a.pdf", displayName = "a.pdf")
            )
        advanceUntilIdle()

        viewModel.onSendIntent(PdfViewerIntent.Share)
        viewModel.onSendIntent(PdfViewerIntent.OpenWith)

        assertFalse(viewModel.state.value.canHandOff)
        verify(exactly = 0) { sharer.share(any()) }
        verify(exactly = 0) { opener.openWith(any()) }
    }

    @Test
    fun aFailedShareIsToldToTheUser() = runTest {
        every { sharer.share(any()) } throws IllegalStateException("no chooser")
        catalogue.value = scanned()
        val viewModel = viewModel(ViewerDocumentRef.Catalogued(UUID))
        advanceUntilIdle()

        viewModel.onSendIntent(PdfViewerIntent.Share)
        val event = viewModel.defaultEvents.first()

        assertTrue(event is UiEvent.ShowMessage)
    }

    /** Printing needs the activity, so it is asked of the screen rather than done here. */
    @Test
    fun printAsksTheScreenWithTheTitleAsJobName() = runTest {
        catalogue.value = scanned()
        val viewModel = viewModel(ViewerDocumentRef.Catalogued(UUID))
        advanceUntilIdle()
        val effects = mutableListOf<PdfViewerEffect>()
        val collector = launch { viewModel.effects.collect { effects += it } }
        advanceUntilIdle()

        viewModel.onSendIntent(PdfViewerIntent.Print)
        advanceUntilIdle()
        collector.cancel()

        assertEquals(PdfViewerEffect.Print(ContentRef(LOCATION), "Invoice"), effects.single())
    }

    // ---------------------------------------------------------------------------------- analytics

    @Test
    fun theScreenViewIsLoggedOncePerOpenedDocument() = runTest {
        viewModel(ViewerDocumentRef.External(uri = EXTERNAL, displayName = "a.pdf"))

        assertEquals(1, analytics.screenViews)
    }

    // ---------------------------------------------------------------------------------- helpers

    private fun viewModel(ref: ViewerDocumentRef, handle: SavedStateHandle = SavedStateHandle()) =
        PdfViewerViewModel(
            ref = ref,
            savedStateHandle = handle,
            observeDocument = ObserveViewerDocumentUseCase(observeDocument),
            observeDisplaySettings =
                ObserveViewerDisplaySettingsUseCase(session, settingsRepository),
            updateDisplaySettings = UpdateViewerDisplaySettingsUseCase(session),
            documentSharer = sharer,
            documentOpener = opener,
            stringProvider = stringProvider,
            analyticsHelper = analytics,
        )

    private fun scanned() =
        ScannedDocument(
            uuid = UUID,
            filename = "Scan_20260924_101500",
            title = "Invoice",
            description = null,
            location = ContentRef(LOCATION),
            capturedAtEpochMillis = 0L,
            sizeBytes = 1024L,
            pageCount = 2,
            thumbnail = null,
        )

    private class RecordingAnalytics : AnalyticsHelper {
        val events = mutableListOf<AnalyticsEvent>()

        val screenViews: Int
            get() = events.count { it.type == AnalyticsEvent.Types.SCREEN_VIEW }

        val settingChanges: List<Pair<String, String>>
            get() =
                events
                    .filter { it.type == AnalyticsEvent.Types.PDF_VIEWER_SETTING_CHANGED }
                    .map { event ->
                        val params = event.extras.associate { it.key to it.value }
                        params.getValue(AnalyticsEvent.ParamKeys.SETTING_NAME) to
                            params.getValue(AnalyticsEvent.ParamKeys.SETTING_VALUE)
                    }

        override fun logEvent(event: AnalyticsEvent) {
            events += event
        }
    }

    private companion object {
        const val UUID = "doc-1"
        const val LOCATION = "content://com.bobbyesp.docucraft.fileprovider/documents/doc-1.pdf"
        const val EXTERNAL = "content://media/external/downloads/37"
    }
}
