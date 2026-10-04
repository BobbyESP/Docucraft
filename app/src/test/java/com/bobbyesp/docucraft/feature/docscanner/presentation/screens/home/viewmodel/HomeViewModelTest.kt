/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.bobbyesp.docucraft.core.domain.StringProvider
import com.bobbyesp.docucraft.core.domain.analytics.AnalyticsEvent
import com.bobbyesp.docucraft.core.domain.notifications.NotificationType
import com.bobbyesp.docucraft.core.domain.repository.AnalyticsHelper
import com.bobbyesp.docucraft.core.util.events.UiEvent
import com.bobbyesp.docucraft.feature.docscanner.FakeDocumentActivityRepository
import com.bobbyesp.docucraft.feature.docscanner.FakeFoldersRepository
import com.bobbyesp.docucraft.feature.docscanner.FakeTagsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.ScanRequestBus
import com.bobbyesp.docucraft.feature.docscanner.domain.SortOption
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.model.RecentDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Tag
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveHomeSectionsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveLibraryUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveRecentDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ProcessDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SaveScanDraftUseCase
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeIntent
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeStatus
import com.bobbyesp.docucraft.feature.docscanner.testDocument
import com.bobbyesp.docucraft.feature.docscanner.testFolder
import com.bobbyesp.docucraft.feature.docscanner.testLinkedDocument
import com.bobbyesp.docucraft.feature.docscanner.testRecent
import com.bobbyesp.scanner.ContentRef
import com.bobbyesp.scanner.DocumentScanner
import com.bobbyesp.scanner.ScanArtifact
import com.bobbyesp.scanner.ScanDraft
import com.bobbyesp.scanner.ScanError
import com.bobbyesp.scanner.ScanOutcome
import com.bobbyesp.scanner.ScanOutputFormat
import com.bobbyesp.scanner.ScanRequest
import com.bobbyesp.scanner.ScannerCapabilities
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
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

/**
 * Covers the reactive document pipeline and the scan lifecycle: the re-entrancy guard, the three
 * ways a session can end, and the widget entry point.
 *
 * There is no scanner engine in here: [FakeDocumentScanner] stands in for one through the
 * [DocumentScanner] port, so every outcome is reachable without a device or Play Services.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val documentScanner = FakeDocumentScanner()
    private val scanRequests = ScanRequestBus()
    private val saveScanDraftUseCase: SaveScanDraftUseCase = mockk()
    private val analyticsHelper: AnalyticsHelper = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fakeDocument(uuid: String = "doc-1") =
        testDocument(uuid = uuid, originalName = "$uuid.pdf", title = "Title $uuid", pageCount = 3)

    private val scannedDraft =
        ScanDraft(
            artifacts = listOf(ScanArtifact.Pdf(ContentRef("content://scan"), 2)),
            capturedAtEpochMillis = 1_234L,
        )

    private fun completedScan() = ScanOutcome.Completed(scannedDraft)

    private fun createViewModel(
        documents: Flow<List<Document.Managed>> = flowOf(emptyList()),
        recents: List<RecentDocument> = emptyList(),
        saveResult: Result<String> = Result.success("doc-1"),
        savedState: SavedStateHandle = SavedStateHandle(),
        pendingScan: ScanOutcome? = null,
        folders: FakeFoldersRepository = FakeFoldersRepository(),
        tags: FakeTagsRepository = FakeTagsRepository(),
    ): HomeViewModel {
        documentScanner.pending = pendingScan
        val observeDocumentsUseCase: ObserveDocumentsUseCase = mockk()
        val stringProvider: StringProvider = mockk(relaxed = true)

        every { observeDocumentsUseCase() } returns documents
        // The whole library comes from the same flow; a tag narrows it down through the tags.
        val observeLibraryUseCase: ObserveLibraryUseCase = mockk()
        every { observeLibraryUseCase(any()) } answers
            {
                val wanted = firstArg<Set<String>>()
                if (wanted.isEmpty()) documents else tags.observeDocumentsWithAll(wanted.toList())
            }
        coEvery { saveScanDraftUseCase(any()) } returns saveResult
        every { stringProvider.getError(any<Throwable>()) } returns "Something went wrong"
        every { stringProvider.get(any(), *anyVararg()) } returns "Something went wrong"

        return HomeViewModel(
            savedStateHandle = savedState,
            documentScanner = documentScanner,
            scanRequests = scanRequests,
            observeDocumentsUseCase = observeDocumentsUseCase,
            observeRecentDocumentsUseCase =
                ObserveRecentDocumentsUseCase(FakeDocumentActivityRepository(recents)),
            observeLibraryUseCase = observeLibraryUseCase,
            observeHomeSectionsUseCase = ObserveHomeSectionsUseCase(folders, tags),
            tags = tags,
            processDocumentsUseCase = ProcessDocumentsUseCase(),
            saveScanDraftUseCase = saveScanDraftUseCase,
            stringProvider = stringProvider,
            analyticsHelper = analyticsHelper,
            defaultDispatcher = testDispatcher,
        )
    }

    /**
     * Collects [viewModel]'s messages into [events] as each one is sent.
     *
     * Unconfined, not the test's own dispatcher: `advanceUntilIdle` does not wait for work in the
     * background scope, so a collector queued there only ran while something else in the ViewModel
     * kept the scheduler busy. These tests passed on the search debounce's timers until search
     * moved to its own screen.
     */
    private fun TestScope.collectEvents(viewModel: HomeViewModel, events: MutableList<UiEvent>) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.defaultEvents.toList(events)
        }
    }

    // ---------------- organization ----------------

    @Test
    fun `Home shows the pinned folders and the sections of the chosen tags`() =
        runTest(testDispatcher) {
            val document = fakeDocument()
            val viewModel =
                createViewModel(
                    documents = flowOf(listOf(document)),
                    folders =
                        FakeFoldersRepository(
                            folders =
                                listOf(
                                    testFolder("pinned", pinnedAtEpochMillis = 1),
                                    testFolder("no"),
                                )
                        ),
                    tags =
                        FakeTagsRepository(
                            tags =
                                listOf(
                                    Tag("shown", "Shown", color = null, homePosition = 0),
                                    Tag("hidden", "Hidden", color = null, homePosition = null),
                                ),
                            documents = listOf(document),
                            assignments = mapOf(document.uuid to setOf("shown")),
                        ),
                )
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(listOf("pinned"), state.pinnedFolders.map { it.uuid })
            assertEquals(listOf("shown"), state.tagSections.map { it.tag.uuid })
            assertEquals(listOf(document), state.tagSections.single().documents)
            assertEquals(2, state.tags.size)
        }

    @Test
    fun `the list is narrowed down to favorites and to a tag, and cleared keeping its order`() =
        runTest(testDispatcher) {
            val favorite = fakeDocument("fav").copy(isFavorite = true)
            val tagged = fakeDocument("tagged")
            val viewModel =
                createViewModel(
                    documents = flowOf(listOf(favorite, tagged)),
                    tags =
                        FakeTagsRepository(
                            tags = listOf(Tag("t", "T", color = null, homePosition = null)),
                            documents = listOf(favorite, tagged),
                            assignments = mapOf("tagged" to setOf("t")),
                        ),
                )
            advanceUntilIdle()

            viewModel.onSendIntent(HomeIntent.ToggleFavoritesFilter)
            advanceUntilIdle()
            assertEquals(listOf("fav"), viewModel.state.value.visibleDocuments.map { it.uuid })

            // What a tag's section offers: only that tag, whatever was chosen before.
            viewModel.onSendIntent(HomeIntent.ShowOnlyTag("t"))
            advanceUntilIdle()
            assertEquals(listOf("tagged"), viewModel.state.value.visibleDocuments.map { it.uuid })
            assertTrue(viewModel.state.value.hasDocuments)

            viewModel.onSendIntent(HomeIntent.ApplySort(SortOption.NameAsc))
            viewModel.onSendIntent(HomeIntent.ClearFilters)
            advanceUntilIdle()
            assertEquals(2, viewModel.state.value.visibleDocuments.size)
            assertEquals(SortOption.NameAsc, viewModel.state.value.filterOptions.sortBy)
        }

    @Test
    fun `a tag that is deleted stops narrowing the list down`() =
        runTest(testDispatcher) {
            val document = fakeDocument()
            val tags =
                FakeTagsRepository(tags = listOf(Tag("t", "T", color = null, homePosition = null)))
            val viewModel = createViewModel(documents = flowOf(listOf(document)), tags = tags)
            advanceUntilIdle()

            viewModel.onSendIntent(HomeIntent.ToggleTagFilter("t"))
            advanceUntilIdle()
            assertTrue(viewModel.state.value.visibleDocuments.isEmpty())

            tags.delete("t")
            advanceUntilIdle()

            assertTrue(viewModel.state.value.filterOptions.tagUuids.isEmpty())
            assertEquals(listOf(document), viewModel.state.value.visibleDocuments)
        }

    // ---------------- documents ----------------

    @Test
    fun `loads documents into Idle state`() =
        runTest(testDispatcher) {
            val documents = listOf(fakeDocument())
            val viewModel = createViewModel(documents = flowOf(documents))

            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(HomeStatus.Idle, state.status)
            assertEquals(documents, state.visibleDocuments)
            assertTrue(state.hasDocuments)
        }

    @Test
    fun `failure while observing documents surfaces Error status`() =
        runTest(testDispatcher) {
            val viewModel =
                createViewModel(documents = flow { throw IllegalStateException("boom") })

            advanceUntilIdle()

            val status = viewModel.state.value.status
            assertTrue(status is HomeStatus.Error)
            assertEquals("Something went wrong", (status as HomeStatus.Error).message)
        }

    /** With a handful of documents, the list already shows them all; a shelf would repeat it. */
    @Test
    fun `a small library has no recents`() =
        runTest(testDispatcher) {
            val documents = List(3) { fakeDocument(uuid = "doc-$it") }
            val viewModel =
                createViewModel(
                    documents = flowOf(documents),
                    recents = documents.map { testRecent(it) },
                )

            advanceUntilIdle()

            assertTrue(viewModel.state.value.recentDocuments.isEmpty())
        }

    /** In the order they were used, whatever the list is sorted by, and only as many as fit. */
    @Test
    fun `recents are the documents used last, in that order and capped`() =
        runTest(testDispatcher) {
            val documents = List(12) { fakeDocument(uuid = "doc-$it") }
            val usedLast = listOf(5, 0, 11, 3, 8, 1, 9, 2, 7, 4).map { documents[it] }
            val viewModel =
                createViewModel(
                    documents = flowOf(documents),
                    recents = usedLast.map { testRecent(it) },
                )

            advanceUntilIdle()

            assertEquals(
                listOf(5, 0, 11, 3, 8, 1, 9, 2).map { "doc-$it" },
                viewModel.state.value.recentDocuments.map { it.document.uuid },
            )
        }

    /** Another app's document is not in the list below: the shelf is the only way back to it. */
    @Test
    fun `a document of another app is on the shelf however small the library`() =
        runTest(testDispatcher) {
            val linked = testLinkedDocument()
            val viewModel =
                createViewModel(
                    documents = flowOf(listOf(fakeDocument())),
                    recents = listOf(testRecent(linked), testRecent(fakeDocument())),
                )

            advanceUntilIdle()

            assertEquals(
                listOf(linked.uuid, "doc-1"),
                viewModel.state.value.recentDocuments.map { it.document.uuid },
            )
        }

    @Test
    fun `ApplySort updates the active sort option`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onSendIntent(HomeIntent.ApplySort(SortOption.NameAsc))

            assertEquals(SortOption.NameAsc, viewModel.state.value.filterOptions.sortBy)
        }

    // ---------------- scanning ----------------

    @Test
    fun `LaunchScanner flips isScanning on immediately and starts a scan`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onSendIntent(HomeIntent.LaunchScanner)

            // Set synchronously by the intent handler, before the scanner is even reached.
            assertTrue(viewModel.state.value.isScanning)

            advanceUntilIdle()

            assertEquals(1, documentScanner.started)
            verify(exactly = 1) {
                analyticsHelper.logEvent(match { it.type == AnalyticsEvent.Types.SCAN_STARTED })
            }
        }

    @Test
    fun `LaunchScanner while a scan is in flight is a no-op`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onSendIntent(HomeIntent.LaunchScanner)
            viewModel.onSendIntent(HomeIntent.LaunchScanner)
            advanceUntilIdle()

            assertEquals(1, documentScanner.started)
        }

    @Test
    fun `a completed scan clears isScanning and saves the document`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            val events = mutableListOf<UiEvent>()
            collectEvents(viewModel, events)

            viewModel.onSendIntent(HomeIntent.LaunchScanner)
            advanceUntilIdle()

            documentScanner.finishWith(completedScan())
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isScanning)
            coVerify { saveScanDraftUseCase(scannedDraft) }
            assertTrue(
                events.any { it is UiEvent.ShowMessage && it.type == NotificationType.Success }
            )
        }

    /** The use case reports failure in its Result rather than by throwing, so it has to be read. */
    @Test
    fun `a save failure is reported instead of congratulating the user`() =
        runTest(testDispatcher) {
            val viewModel =
                createViewModel(saveResult = Result.failure(IllegalStateException("disk full")))
            advanceUntilIdle()

            val events = mutableListOf<UiEvent>()
            collectEvents(viewModel, events)

            viewModel.onSendIntent(HomeIntent.LaunchScanner)
            advanceUntilIdle()
            documentScanner.finishWith(completedScan())
            advanceUntilIdle()

            assertTrue(events.isNotEmpty())
            assertTrue(
                events.all { it is UiEvent.ShowMessage && it.type == NotificationType.Error }
            )
        }

    @Test
    fun `cancelling the scan is silent and saves nothing`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            val events = mutableListOf<UiEvent>()
            collectEvents(viewModel, events)

            viewModel.onSendIntent(HomeIntent.LaunchScanner)
            advanceUntilIdle()

            documentScanner.finishWith(ScanOutcome.Cancelled)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isScanning)
            coVerify(exactly = 0) { saveScanDraftUseCase(any()) }
            assertTrue("backing out of the scanner should not nag the user", events.isEmpty())
            verify(exactly = 1) {
                analyticsHelper.logEvent(match { it.type == AnalyticsEvent.Types.SCAN_CANCELLED })
            }
        }

    @Test
    fun `a failed scan tells the user and saves nothing`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            val events = mutableListOf<UiEvent>()
            collectEvents(viewModel, events)

            viewModel.onSendIntent(HomeIntent.LaunchScanner)
            advanceUntilIdle()

            documentScanner.finishWith(ScanOutcome.Failed(ScanError.EngineUnavailable))
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isScanning)
            coVerify(exactly = 0) { saveScanDraftUseCase(any()) }
            assertTrue(
                events.any { it is UiEvent.ShowMessage && it.type == NotificationType.Error }
            )
            verify(exactly = 1) {
                analyticsHelper.logEvent(match { it.type == AnalyticsEvent.Types.SCAN_FAILED })
            }
            verify(exactly = 0) {
                analyticsHelper.logEvent(match { it.type == AnalyticsEvent.Types.SCAN_CANCELLED })
            }
        }

    /**
     * The home screen widget reaches the app as an Intent, not as a UI intent. Taking the request
     * is what stops a second state holder acting on the same one.
     */
    @Test
    fun `an external scan request starts a scan and is taken`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            scanRequests.request()
            advanceUntilIdle()

            assertEquals(1, documentScanner.started)
            assertTrue(viewModel.state.value.isScanning)
            assertFalse("The request should have been taken", scanRequests.isPending.value)
        }

    /**
     * The catalogue is not always on screen when the widget is pressed. The request stands, and is
     * honoured once this exists — rather than waiting silently and then firing unasked.
     */
    @Test
    fun `a scan requested before the catalogue existed is honoured when it arrives`() =
        runTest(testDispatcher) {
            scanRequests.request()

            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(1, documentScanner.started)
            assertTrue(viewModel.state.value.isScanning)
        }

    // ---------------- surviving the process ----------------

    /** The flag is what a rebuilt ViewModel has to go on, so it has to be written. */
    @Test
    fun `a scan in flight is remembered where it survives process death`() =
        runTest(testDispatcher) {
            val savedState = SavedStateHandle()
            val viewModel = createViewModel(savedState = savedState)
            advanceUntilIdle()

            viewModel.onSendIntent(HomeIntent.LaunchScanner)
            advanceUntilIdle()
            assertEquals(true, savedState.get<Boolean>("scan_in_flight"))

            documentScanner.finishWith(ScanOutcome.Cancelled)
            advanceUntilIdle()
            assertEquals(false, savedState.get<Boolean>("scan_in_flight"))
        }

    @Test
    fun `a scan that outlived the process is rejoined and saved`() =
        runTest(testDispatcher) {
            val viewModel =
                createViewModel(
                    savedState = SavedStateHandle(mapOf("scan_in_flight" to true)),
                    pendingScan = ScanOutcome.Completed(scannedDraft),
                )

            // The spinner comes back with the session, before its outcome is known.
            assertTrue(viewModel.state.value.isScanning)

            advanceUntilIdle()

            assertEquals(1, documentScanner.resumed)
            assertEquals(0, documentScanner.started)
            coVerify { saveScanDraftUseCase(scannedDraft) }
            assertFalse(viewModel.state.value.isScanning)
        }

    /** The flag can be stale: the process may have died before anything was ever launched. */
    @Test
    fun `nothing left to rejoin just stops the spinner`() =
        runTest(testDispatcher) {
            val viewModel =
                createViewModel(
                    savedState = SavedStateHandle(mapOf("scan_in_flight" to true)),
                    pendingScan = null,
                )
            advanceUntilIdle()

            assertEquals(1, documentScanner.resumed)
            assertFalse(viewModel.state.value.isScanning)
            coVerify(exactly = 0) { saveScanDraftUseCase(any()) }
        }

    @Test
    fun `a ViewModel with no scan in flight does not go looking for one`() =
        runTest(testDispatcher) {
            createViewModel()
            advanceUntilIdle()

            assertEquals(0, documentScanner.resumed)
        }

    /** A scanner whose session ends when the test says so, and however the test says. */
    private class FakeDocumentScanner : DocumentScanner {

        override val capabilities =
            ScannerCapabilities(
                supportedOutputs = setOf(ScanOutputFormat.PDF),
                supportsGalleryImport = true,
                maxPages = null,
                requiresCameraPermission = false,
            )

        private val session = CompletableDeferred<ScanOutcome>()

        var started = 0
            private set

        var resumed = 0
            private set

        /** What a session that outlived the process turns out to have produced. */
        var pending: ScanOutcome? = null

        override suspend fun scan(request: ScanRequest): ScanOutcome {
            started++
            return session.await()
        }

        override suspend fun resumePendingScan(): ScanOutcome? {
            resumed++
            return pending
        }

        fun finishWith(outcome: ScanOutcome) {
            session.complete(outcome)
        }
    }
}
