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
import com.bobbyesp.docucraft.feature.docscanner.FakeDocumentActivityRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.model.DocumentAvailability
import com.bobbyesp.docucraft.feature.docscanner.domain.sharing.DocumentSharer
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.RecordDocumentAvailabilityUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.RecordDocumentOpenedUseCase
import com.bobbyesp.docucraft.feature.docscanner.testDocument
import com.bobbyesp.docucraft.feature.pdfviewer.FakePageContentProvider
import com.bobbyesp.docucraft.feature.pdfviewer.data.settings.InMemoryViewerSessionSettings
import com.bobbyesp.docucraft.feature.pdfviewer.domain.actions.DocumentOpener
import com.bobbyesp.docucraft.feature.pdfviewer.domain.links.LinkAction
import com.bobbyesp.docucraft.feature.pdfviewer.domain.links.ResolveLinkUseCase
import com.bobbyesp.docucraft.feature.pdfviewer.domain.model.ViewerDocumentRef
import com.bobbyesp.docucraft.feature.pdfviewer.domain.usecase.ObserveViewerDisplaySettingsUseCase
import com.bobbyesp.docucraft.feature.pdfviewer.domain.usecase.ObserveViewerDocumentUseCase
import com.bobbyesp.docucraft.feature.pdfviewer.domain.usecase.UpdateViewerDisplaySettingsUseCase
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.ViewerLoadError
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.contract.PdfViewerEffect
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.contract.PdfViewerIntent
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.contract.ViewerDocumentState
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.selection.PageTextState
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.selection.TextUnavailable
import com.bobbyesp.docucraft.feature.pdfviewer.textPage
import com.bobbyesp.documentcontent.DocumentSelection
import com.bobbyesp.documentcontent.NormalizedPoint
import com.bobbyesp.documentcontent.NormalizedRect
import com.bobbyesp.documentcontent.PageContentResult
import com.bobbyesp.documentcontent.PageLink
import com.bobbyesp.documentcontent.TextCaret
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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

    private val catalogue = MutableStateFlow<Document?>(null)
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
    private val session = InMemoryViewerSessionSettings()
    private val activity = FakeDocumentActivityRepository()

    // Page 0 "alpha beta", 1 "gamma", 2 a scan, 3 "delta epsilon", 4 "zeta".
    private val content =
        FakePageContentProvider(
            mapOf(
                0 to textPage("alpha", "beta").copy(links = listOf(LINK)),
                1 to textPage("gamma"),
                2 to PageContentResult.NoText,
                3 to textPage("delta", "epsilon"),
                4 to textPage("zeta"),
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
    fun `a catalogued document is loading until the catalogue answers`() = runTest {
        val viewModel = viewModel(ViewerDocumentRef.Catalogued(UUID))

        assertEquals(ViewerDocumentState.Loading, viewModel.state.value.document)

        catalogue.value = scanned()
        advanceUntilIdle()

        val open = viewModel.state.value.document as ViewerDocumentState.Open
        assertEquals("Invoice", open.document.title)
    }

    /** Waiting and leaving are different answers: a deleted document must close, not spin. */
    @Test
    fun `a deleted document is gone rather than loading`() = runTest {
        catalogue.value = scanned()
        val viewModel = viewModel(ViewerDocumentRef.Catalogued(UUID))
        advanceUntilIdle()

        catalogue.value = null
        advanceUntilIdle()

        assertEquals(ViewerDocumentState.Gone, viewModel.state.value.document)
    }

    @Test
    fun `an external document opens straight away`() = runTest {
        val viewModel = viewModel(ViewerDocumentRef.External(uri = EXTERNAL, displayName = "a.pdf"))
        advanceUntilIdle()

        val open = viewModel.state.value.document as ViewerDocumentState.Open
        assertEquals("a.pdf", open.document.title)
        assertEquals(EXTERNAL, open.document.uri)
    }

    // --------------------------------------------------------------------------------- settings

    @Test
    fun `changing the fit mode is kept and reported`() = runTest {
        val viewModel = viewModel(ViewerDocumentRef.Catalogued(UUID))
        advanceUntilIdle()

        viewModel.onSendIntent(PdfViewerIntent.SetFitMode(ViewerFitMode.WIDTH))

        assertEquals(ViewerFitMode.WIDTH, viewModel.state.value.display?.fitMode)
        assertEquals("fit_mode" to "WIDTH", analytics.settingChanges.single())
    }

    @Test
    fun `toggling night mode reports the new value`() = runTest {
        val viewModel = viewModel(ViewerDocumentRef.Catalogued(UUID))
        advanceUntilIdle()

        viewModel.onSendIntent(PdfViewerIntent.ToggleNightMode)

        assertTrue(viewModel.state.value.display!!.nightMode)
        assertEquals("night_mode" to "true", analytics.settingChanges.single())
    }

    /** Showing the document before its settings are known would lay it out twice. */
    @Test
    fun `the document is only ready once its settings are known`() = runTest {
        catalogue.value = scanned()
        val viewModel = viewModel(ViewerDocumentRef.Catalogued(UUID))

        assertEquals(null, viewModel.state.value.readyDocument)

        advanceUntilIdle()

        assertEquals(ViewerDisplaySettings.Factory, viewModel.state.value.display)
        assertEquals("Invoice", viewModel.state.value.readyDocument?.title)
    }

    /** D2: the same document opened again in the same session keeps what the user chose. */
    @Test
    fun `a change is remembered for the document for the session`() = runTest {
        val first = viewModel(ViewerDocumentRef.Catalogued(UUID))
        advanceUntilIdle()
        first.onSendIntent(PdfViewerIntent.SetFitMode(ViewerFitMode.HEIGHT))

        val reopened = viewModel(ViewerDocumentRef.Catalogued(UUID))
        advanceUntilIdle()

        assertEquals(ViewerFitMode.HEIGHT, reopened.state.value.display?.fitMode)
    }

    @Test
    fun `a change is also kept in the entry's saved state`() = runTest {
        val handle = SavedStateHandle()
        val viewModel = viewModel(ViewerDocumentRef.Catalogued(UUID), handle)
        advanceUntilIdle()

        viewModel.onSendIntent(PdfViewerIntent.ToggleNightMode)
        advanceUntilIdle()

        assertEquals(true, handle.get<Boolean>("viewer_night_mode"))
        assertEquals("WIDTH", handle.get<String>("viewer_fit_mode"))
    }

    /**
     * Changed in one entry, closed and reopened in another: the new entry shows the session's
     * choice, and has to keep it too, or a process death would bring the defaults back.
     */
    @Test
    fun `a reopened document keeps the session's choice in its own saved state`() = runTest {
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
    fun `an untouched document keeps nothing in its saved state`() = runTest {
        val handle = SavedStateHandle()
        viewModel(ViewerDocumentRef.Catalogued(UUID), handle)
        advanceUntilIdle()

        assertEquals(null, handle.get<Boolean>("viewer_night_mode"))
    }

    /**
     * After a process death the session memory is empty, but the restored entry still knows what
     * its document was showing, and that counts as the same session.
     */
    @Test
    fun `after a process death the restored entry keeps its settings`() = runTest {
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
    fun `share and open with hand the document's location over`() = runTest {
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
    fun `a document that cannot leave the app is not handed off`() = runTest {
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
    fun `a failed share is told to the user`() = runTest {
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
    fun `print asks the screen, with the title as job name`() = runTest {
        catalogue.value = scanned()
        val viewModel = viewModel(ViewerDocumentRef.Catalogued(UUID))
        advanceUntilIdle()
        val effects = collectEffects(viewModel)

        viewModel.onSendIntent(PdfViewerIntent.Print)
        advanceUntilIdle()

        assertEquals(PdfViewerEffect.Print(ContentRef(LOCATION), "Invoice"), effects.single())
    }

    // ---------------------------------------------------------------------------------- text

    @Test
    fun `the pages on screen and either side are read`() = runTest {
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
    fun `moving on forgets pages off screen, but not where the selection ends`() = runTest {
        val viewModel = openedExternal()
        viewModel.onSendIntent(PdfViewerIntent.VisiblePagesChanged(0..0))
        advanceUntilIdle()
        viewModel.onSendIntent(PdfViewerIntent.Select(selection(0, 0, 0, 1)))

        viewModel.onSendIntent(PdfViewerIntent.VisiblePagesChanged(3..3))
        advanceUntilIdle()

        assertEquals(setOf(0, 2, 3, 4), viewModel.state.value.pageText.keys)
    }

    @Test
    fun `copying reads every page of the selection, then lets go of it`() = runTest {
        val viewModel = openedExternal()
        viewModel.onSendIntent(PdfViewerIntent.VisiblePagesChanged(0..0))
        advanceUntilIdle()
        val effects = collectEffects(viewModel)

        // From "eta" on page 0 to "del" on page 3, inside both words, over a scanned page that is
        // left out.
        viewModel.onSendIntent(PdfViewerIntent.Select(selection(0, 7, 3, 3)))
        viewModel.onSendIntent(PdfViewerIntent.CopySelection)
        advanceUntilIdle()

        assertEquals(PdfViewerEffect.CopyText("eta\ngamma\ndel"), effects.single())
        assertEquals(null, viewModel.state.value.selection)
    }

    @Test
    fun `select all takes in the whole of every page the selection touches`() = runTest {
        val viewModel = openedExternal()
        viewModel.onSendIntent(PdfViewerIntent.Select(selection(0, 7, 1, 2)))

        viewModel.onSendIntent(PdfViewerIntent.SelectAll)
        advanceUntilIdle()

        // "alpha beta" and "gamma", whole.
        assertEquals(selection(0, 0, 1, 5), viewModel.state.value.selection)
    }

    @Test
    fun `a page without text is explained once a document`() = runTest {
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
    fun `the content session closes with the view model`() = runTest {
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

    // ---------------------------------------------------------------------------------- links

    @Test
    fun `an external link is shown before anything opens`() = runTest {
        val viewModel = openedExternal()
        val effects = collectEffects(viewModel)

        viewModel.onSendIntent(PdfViewerIntent.TapLink(0, web("https://a.b@example.com/x"), 3))
        advanceUntilIdle()

        val preview = viewModel.state.value.linkPreview!!
        assertEquals(0, preview.page)
        assertEquals(
            LinkAction.OpenWeb("https://a.b@example.com/x", "example.com", true),
            preview.action,
        )
        assertTrue("nothing opens from the tap itself", effects.isEmpty())
    }

    @Test
    fun `opening the previewed link hands it to the screen and closes the preview`() = runTest {
        val viewModel = openedExternal()
        val effects = collectEffects(viewModel)
        viewModel.onSendIntent(PdfViewerIntent.TapLink(0, web("https://example.com"), 3))

        viewModel.onSendIntent(PdfViewerIntent.OpenPreviewedLink)
        advanceUntilIdle()

        assertEquals(
            PdfViewerEffect.OpenLink(
                LinkAction.OpenWeb("https://example.com", "example.com", true)
            ),
            effects.single(),
        )
        assertEquals(null, viewModel.state.value.linkPreview)
    }

    @Test
    fun `copying the link copies where it goes`() = runTest {
        val viewModel = openedExternal()
        val effects = collectEffects(viewModel)
        viewModel.onSendIntent(PdfViewerIntent.TapLink(0, web("mailto:hola@example.com"), 3))

        viewModel.onSendIntent(PdfViewerIntent.CopyPreviewedLink)
        advanceUntilIdle()

        assertEquals(PdfViewerEffect.CopyText("hola@example.com"), effects.single())
    }

    @Test
    fun `a refused link is shown but never opened`() = runTest {
        val viewModel = openedExternal()
        val effects = collectEffects(viewModel)
        viewModel.onSendIntent(PdfViewerIntent.TapLink(0, web("javascript:alert(1)"), 3))
        assertFalse(viewModel.state.value.linkPreview!!.canOpen)

        viewModel.onSendIntent(PdfViewerIntent.OpenPreviewedLink)
        advanceUntilIdle()

        assertTrue(effects.isEmpty())
    }

    @Test
    fun `an internal link is followed at once, with the way back`() = runTest {
        val viewModel = openedExternal()
        val effects = collectEffects(viewModel)
        val position = NormalizedPoint(0f, 0.3f)

        viewModel.onSendIntent(
            PdfViewerIntent.TapLink(
                0,
                PageLink.Internal(listOf(AREA), pageIndex = 2, position = position),
                3,
            )
        )
        advanceUntilIdle()

        assertEquals(PdfViewerEffect.GoToPage(2, position, from = 0), effects.single())
        assertEquals(null, viewModel.state.value.linkPreview)
    }

    @Test
    fun `tapping a link lets go of the selection`() = runTest {
        val viewModel = openedExternal()
        viewModel.onSendIntent(PdfViewerIntent.Select(selection(0, 0, 0, 3)))

        viewModel.onSendIntent(PdfViewerIntent.TapLink(0, web("https://example.com"), 3))

        assertEquals(null, viewModel.state.value.selection)
    }

    // ---------------------------------------------------------------------------------- analytics

    @Test
    fun `the screen view is logged once per opened document`() = runTest {
        viewModel(ViewerDocumentRef.External(uri = EXTERNAL, displayName = "a.pdf"))

        assertEquals(1, analytics.screenViews)
    }

    // ---------------------------------------------------------------------------------- activity

    /** What puts a document at the front of Recents. */
    @Test
    fun `opening a catalogued document is noted once`() = runTest {
        catalogue.value = scanned()
        viewModel(ViewerDocumentRef.Catalogued(UUID))
        advanceUntilIdle()

        assertEquals(listOf(UUID), activity.opened)
    }

    @Test
    fun `a document that loads is noted as available`() = runTest {
        catalogue.value = scanned()
        val viewModel = viewModel(ViewerDocumentRef.Catalogued(UUID))

        viewModel.onSendIntent(PdfViewerIntent.DocumentLoaded)
        advanceUntilIdle()

        assertEquals(listOf(UUID to DocumentAvailability.AVAILABLE), activity.availability)
    }

    /** Recents shows these two instead of failing when the document is tapped again. */
    @Test
    fun `a file that is gone or may not be read is noted as such`() = runTest {
        catalogue.value = scanned()
        val viewModel = viewModel(ViewerDocumentRef.Catalogued(UUID))

        viewModel.onSendIntent(PdfViewerIntent.DocumentFailedToLoad(ViewerLoadError.NotFound))
        viewModel.onSendIntent(PdfViewerIntent.DocumentFailedToLoad(ViewerLoadError.AccessDenied))
        advanceUntilIdle()

        assertEquals(
            listOf(
                UUID to DocumentAvailability.NOT_FOUND,
                UUID to DocumentAvailability.NO_PERMISSION,
            ),
            activity.availability,
        )
    }

    /** It could not be shown, but it was reached: the file is where the catalogue says. */
    @Test
    fun `a protected or damaged document is still noted as available`() = runTest {
        catalogue.value = scanned()
        val viewModel = viewModel(ViewerDocumentRef.Catalogued(UUID))

        viewModel.onSendIntent(
            PdfViewerIntent.DocumentFailedToLoad(ViewerLoadError.PasswordProtected)
        )
        viewModel.onSendIntent(PdfViewerIntent.DocumentFailedToLoad(ViewerLoadError.Damaged))
        advanceUntilIdle()

        assertEquals(
            listOf(UUID to DocumentAvailability.AVAILABLE, UUID to DocumentAvailability.AVAILABLE),
            activity.availability,
        )
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
            resolveLink = ResolveLinkUseCase(),
            recordOpened = RecordDocumentOpenedUseCase(activity),
            recordAvailability = RecordDocumentAvailabilityUseCase(activity),
        )

    private fun TestScope.openedExternal(): PdfViewerViewModel =
        viewModel(ViewerDocumentRef.External(uri = EXTERNAL, displayName = "a.pdf")).also {
            advanceUntilIdle()
        }

    private fun TestScope.collectEffects(viewModel: PdfViewerViewModel): List<PdfViewerEffect> {
        val effects = mutableListOf<PdfViewerEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.effects.collect { effects += it }
        }
        advanceUntilIdle()
        return effects
    }

    private fun web(uri: String) = PageLink.External(listOf(AREA), uri)

    private fun selection(startPage: Int, startOffset: Int, endPage: Int, endOffset: Int) =
        DocumentSelection(TextCaret(startPage, startOffset), TextCaret(endPage, endOffset))

    private fun scanned() =
        testDocument(
            uuid = UUID,
            originalName = "Scan_20260924_101500",
            title = "Invoice",
            location = ContentRef(LOCATION),
            createdAtEpochMillis = 0L,
            sizeBytes = 1024L,
            pageCount = 2,
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
        val AREA = NormalizedRect(0.1f, 0.1f, 0.3f, 0.12f)
        val LINK = PageLink.External(listOf(NormalizedRect(0.1f, 0.1f, 0.2f, 0.12f)), "https://a.b")
        const val LOCATION = "content://com.bobbyesp.docucraft.fileprovider/documents/doc-1.pdf"
        const val EXTERNAL = "content://media/external/downloads/37"
    }
}
