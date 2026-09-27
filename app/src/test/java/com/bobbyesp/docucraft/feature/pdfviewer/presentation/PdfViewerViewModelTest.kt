/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
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
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.selection.PageTextState
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.selection.TextUnavailable
import com.bobbyesp.documentcontent.ContentOrigin
import com.bobbyesp.documentcontent.DocumentSelection
import com.bobbyesp.documentcontent.DocumentSource
import com.bobbyesp.documentcontent.NormalizedRect
import com.bobbyesp.documentcontent.PageContentProvider
import com.bobbyesp.documentcontent.PageContentResult
import com.bobbyesp.documentcontent.PageContentSession
import com.bobbyesp.documentcontent.PageLink
import com.bobbyesp.documentcontent.PageText
import com.bobbyesp.documentcontent.TextCaret
import com.bobbyesp.documentcontent.TextLine
import com.bobbyesp.documentcontent.TextWord
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
import kotlinx.coroutines.test.TestScope
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

    // Page 0 "alpha beta", 1 "gamma", 2 a scan, 3 "delta epsilon", 4 "zeta".
    private val content =
        FakeContent(
            mapOf(
                0 to text("alpha", "beta").copy(links = listOf(LINK)),
                1 to text("gamma"),
                2 to PageContentResult.NoText,
                3 to text("delta", "epsilon"),
                4 to text("zeta"),
            )
        )

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

    // ---------------------------------------------------------------------------------- text

    @Test
    fun thePagesOnScreenAndEitherSideAreRead() = runTest {
        val viewModel = openedExternal()

        viewModel.onSendIntent(PdfViewerIntent.VisiblePagesChanged(1..1))
        advanceUntilIdle()

        assertEquals(setOf(0, 1, 2), viewModel.state.value.pageText.keys)
        assertEquals(PageTextState.NoText, viewModel.state.value.pageText[2])
        // Their links come with them, in the same read.
        assertEquals(setOf(0, 1, 2), viewModel.state.value.pageLinks.keys)
        assertEquals(listOf(LINK), viewModel.state.value.pageLinks[0])
        assertEquals("one session for the document", 1, content.opened)
    }

    @Test
    fun movingOnForgetsPagesOffScreenButNotWhereTheSelectionEnds() = runTest {
        val viewModel = openedExternal()
        viewModel.onSendIntent(PdfViewerIntent.VisiblePagesChanged(0..0))
        advanceUntilIdle()
        viewModel.onSendIntent(PdfViewerIntent.Select(selection(0, 0, 0, 1)))

        viewModel.onSendIntent(PdfViewerIntent.VisiblePagesChanged(3..3))
        advanceUntilIdle()

        assertEquals(setOf(0, 2, 3, 4), viewModel.state.value.pageText.keys)
    }

    @Test
    fun copyingReadsEveryPageOfTheSelectionThenLetsGoOfIt() = runTest {
        val viewModel = openedExternal()
        viewModel.onSendIntent(PdfViewerIntent.VisiblePagesChanged(0..0))
        advanceUntilIdle()
        val effects = mutableListOf<PdfViewerEffect>()
        val collector = launch { viewModel.effects.collect { effects += it } }
        advanceUntilIdle()

        // From "eta" on page 0 to "del" on page 3, inside both words, over a scanned page that is
        // left out.
        viewModel.onSendIntent(PdfViewerIntent.Select(selection(0, 7, 3, 3)))
        viewModel.onSendIntent(PdfViewerIntent.CopySelection)
        advanceUntilIdle()
        collector.cancel()

        assertEquals(PdfViewerEffect.CopyText("eta\ngamma\ndel"), effects.single())
        assertEquals(null, viewModel.state.value.selection)
    }

    @Test
    fun selectAllTakesInTheWholeOfEveryPageTheSelectionTouches() = runTest {
        val viewModel = openedExternal()
        viewModel.onSendIntent(PdfViewerIntent.Select(selection(0, 7, 1, 2)))

        viewModel.onSendIntent(PdfViewerIntent.SelectAll)
        advanceUntilIdle()

        // "alpha beta" and "gamma", whole.
        assertEquals(selection(0, 0, 1, 5), viewModel.state.value.selection)
    }

    @Test
    fun aPageWithoutTextIsExplainedOnceADocument() = runTest {
        val viewModel = openedExternal()

        viewModel.onSendIntent(PdfViewerIntent.NothingToSelect(TextUnavailable.ImageOnly))
        viewModel.onSendIntent(PdfViewerIntent.NothingToSelect(TextUnavailable.ImageOnly))
        viewModel.onSendIntent(PdfViewerIntent.NothingToSelect(TextUnavailable.UnsupportedDevice))
        advanceUntilIdle()
        val events = mutableListOf<UiEvent>()
        val collector = launch { viewModel.defaultEvents.collect { events += it } }
        advanceUntilIdle()
        collector.cancel()

        assertEquals("once for each reason", 2, events.size)
    }

    @Test
    fun theContentSessionClosesWithTheViewModel() = runTest {
        val store = ViewModelStore()
        val viewModel =
            ViewModelProvider(
                store,
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        viewModel(ViewerDocumentRef.External(uri = EXTERNAL, displayName = "a.pdf"))
                            as T
                },
            )[PdfViewerViewModel::class.java]
        advanceUntilIdle()
        viewModel.onSendIntent(PdfViewerIntent.VisiblePagesChanged(0..0))
        advanceUntilIdle()

        store.clear()

        assertTrue(content.sessions.single().closed)
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
            contentProvider = content,
        )

    private fun TestScope.openedExternal(): PdfViewerViewModel =
        viewModel(ViewerDocumentRef.External(uri = EXTERNAL, displayName = "a.pdf")).also {
            advanceUntilIdle()
        }

    private fun selection(startPage: Int, startOffset: Int, endPage: Int, endOffset: Int) =
        DocumentSelection(TextCaret(startPage, startOffset), TextCaret(endPage, endOffset))

    private fun text(vararg words: String) =
        PageContentResult.Available(
            text =
                PageText(
                    lines =
                        listOf(
                            TextLine(
                                words.mapIndexed { i, word ->
                                    TextWord(
                                        word,
                                        NormalizedRect(0.1f * i, 0.1f, 0.1f * i + 0.08f, 0.14f),
                                    )
                                }
                            )
                        ),
                    origin = ContentOrigin.EMBEDDED,
                ),
            links = emptyList(),
        )

    private class FakeContent(private val pages: Map<Int, PageContentResult>) :
        PageContentProvider {
        val sessions = mutableListOf<Session>()
        val opened: Int
            get() = sessions.size

        override val origin = ContentOrigin.EMBEDDED

        override suspend fun open(document: DocumentSource): PageContentSession =
            Session(pages).also { sessions += it }

        class Session(private val pages: Map<Int, PageContentResult>) : PageContentSession {
            var closed = false

            override suspend fun page(index: Int): PageContentResult =
                pages[index] ?: PageContentResult.Failed(IndexOutOfBoundsException("$index"))

            override fun close() {
                closed = true
            }
        }
    }

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
        val LINK = PageLink.External(listOf(NormalizedRect(0.1f, 0.1f, 0.2f, 0.12f)), "https://a.b")
        const val LOCATION = "content://com.bobbyesp.docucraft.fileprovider/documents/doc-1.pdf"
        const val EXTERNAL = "content://media/external/downloads/37"
    }
}
