/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.bobbyesp.docucraft.core.domain.StringProvider
import com.bobbyesp.docucraft.core.domain.analytics.AnalyticsEvent
import com.bobbyesp.docucraft.core.domain.notifications.NotificationType
import com.bobbyesp.docucraft.core.domain.repository.AnalyticsHelper
import com.bobbyesp.docucraft.core.util.events.UiEvent
import com.bobbyesp.docucraft.feature.docscanner.domain.ScanRequestBus
import com.bobbyesp.docucraft.feature.docscanner.domain.SortOption
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.sharing.DocumentExporter
import com.bobbyesp.docucraft.feature.docscanner.domain.sharing.DocumentSharer
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.DeleteDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.GetDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ProcessDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SaveScanDraftUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.UpdateDocumentFieldsUseCase
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeIntent
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeStatus
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
 * Note there is no scanner engine anywhere in here. That is what the [DocumentScanner] port buys:
 * [FakeDocumentScanner] stands in for one, so every outcome is reachable without a device and
 * without Play Services.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var documentScanner: FakeDocumentScanner
    private lateinit var scanRequests: ScanRequestBus
    private lateinit var observeDocumentsUseCase: ObserveDocumentsUseCase
    private lateinit var processDocumentsUseCase: ProcessDocumentsUseCase
    private lateinit var getDocumentUseCase: GetDocumentUseCase
    private lateinit var saveScanDraftUseCase: SaveScanDraftUseCase
    private lateinit var deleteDocumentUseCase: DeleteDocumentUseCase
    private lateinit var documentSharer: DocumentSharer
    private lateinit var documentExporter: DocumentExporter
    private lateinit var updateDocumentFieldsUseCase: UpdateDocumentFieldsUseCase
    private lateinit var stringProvider: StringProvider
    private lateinit var analyticsHelper: AnalyticsHelper

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
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

    private fun fakeDocument(uuid: String = "doc-1") =
        ScannedDocument(
            id = 1,
            uuid = uuid,
            filename = "$uuid.pdf",
            title = "Title $uuid",
            description = null,
            path = mockk<Uri>(relaxed = true),
            createdTimestamp = 1_000L,
            fileSize = 2_048L,
            pageCount = 3,
            thumbnail = null,
        )

    private val scannedDraft =
        ScanDraft(
            artifacts = listOf(ScanArtifact.Pdf(ContentRef("content://scan"), 2)),
            capturedAtEpochMillis = 1_234L,
        )

    private fun completedScan() = ScanOutcome.Completed(scannedDraft)

    private fun createViewModel(
        documents: Flow<List<ScannedDocument>> = flowOf(emptyList()),
        saveResult: Result<ContentRef> = Result.success(ContentRef("content://stored")),
        savedState: SavedStateHandle = SavedStateHandle(),
        pendingScan: ScanOutcome? = null,
    ): HomeViewModel {
        documentScanner = FakeDocumentScanner().apply { pending = pendingScan }
        scanRequests = ScanRequestBus()
        observeDocumentsUseCase = mockk()
        processDocumentsUseCase = mockk()
        getDocumentUseCase = mockk()
        saveScanDraftUseCase = mockk()
        deleteDocumentUseCase = mockk(relaxed = true)
        documentSharer = mockk(relaxed = true)
        documentExporter = mockk()
        updateDocumentFieldsUseCase = mockk(relaxed = true)
        stringProvider = mockk(relaxed = true)
        analyticsHelper = mockk(relaxed = true)

        coEvery { observeDocumentsUseCase() } returns documents
        coEvery { processDocumentsUseCase(any(), any(), any(), any()) } answers { firstArg() }
        coEvery { saveScanDraftUseCase(any(), any()) } returns saveResult
        every { stringProvider.getError(any<Throwable>()) } returns "Something went wrong"
        every { stringProvider.get(any(), *anyVararg()) } returns "Something went wrong"

        return HomeViewModel(
            savedStateHandle = savedState,
            documentScanner = documentScanner,
            scanRequests = scanRequests,
            observeDocumentsUseCase = observeDocumentsUseCase,
            processDocumentsUseCase = processDocumentsUseCase,
            getDocumentUseCase = getDocumentUseCase,
            saveScanDraftUseCase = saveScanDraftUseCase,
            deleteDocumentUseCase = deleteDocumentUseCase,
            documentSharer = documentSharer,
            documentExporter = documentExporter,
            updateDocumentFieldsUseCase = updateDocumentFieldsUseCase,
            stringProvider = stringProvider,
            analyticsHelper = analyticsHelper,
            defaultDispatcher = testDispatcher,
        )
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

    @Test
    fun `UpdateSearch stores the query in state`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onSendIntent(HomeIntent.UpdateSearch("invoice"))

            assertEquals("invoice", viewModel.state.value.searchQuery)
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
            backgroundScope.launch { viewModel.defaultEvents.toList(events) }

            viewModel.onSendIntent(HomeIntent.LaunchScanner)
            advanceUntilIdle()

            documentScanner.finishWith(completedScan())
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isScanning)
            coVerify { saveScanDraftUseCase(scannedDraft, any()) }
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
            backgroundScope.launch { viewModel.defaultEvents.toList(events) }

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
            backgroundScope.launch { viewModel.defaultEvents.toList(events) }

            viewModel.onSendIntent(HomeIntent.LaunchScanner)
            advanceUntilIdle()

            documentScanner.finishWith(ScanOutcome.Cancelled)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isScanning)
            coVerify(exactly = 0) { saveScanDraftUseCase(any(), any()) }
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
            backgroundScope.launch { viewModel.defaultEvents.toList(events) }

            viewModel.onSendIntent(HomeIntent.LaunchScanner)
            advanceUntilIdle()

            documentScanner.finishWith(ScanOutcome.Failed(ScanError.EngineUnavailable))
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isScanning)
            coVerify(exactly = 0) { saveScanDraftUseCase(any(), any()) }
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

    /** The home screen widget reaches the app as an Intent, not as a UI intent. */
    @Test
    fun `an external scan request starts a scan`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            scanRequests.request()
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
            coVerify { saveScanDraftUseCase(scannedDraft, any()) }
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
            coVerify(exactly = 0) { saveScanDraftUseCase(any(), any()) }
        }

    @Test
    fun `a ViewModel with no scan in flight does not go looking for one`() =
        runTest(testDispatcher) {
            createViewModel()
            advanceUntilIdle()

            assertEquals(0, documentScanner.resumed)
        }
}
