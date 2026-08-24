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
import com.bobbyesp.docucraft.feature.docscanner.domain.ScannerManager
import com.bobbyesp.docucraft.feature.docscanner.domain.SortOption
import com.bobbyesp.docucraft.feature.docscanner.domain.model.RawScanResult
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.DeleteDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ExportDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.GetDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ProcessDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SaveScannedDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ShareDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.UpdateDocumentFieldsUseCase
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeIntent
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Covers the reactive document pipeline and the scan-launch lifecycle (the
 * [HomeIntent.LaunchScanner] "no in-app feedback while waiting for the system scanner" fix, and its
 * re-entrancy guard).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var scannerManager: ScannerManager
    private lateinit var observeDocumentsUseCase: ObserveDocumentsUseCase
    private lateinit var processDocumentsUseCase: ProcessDocumentsUseCase
    private lateinit var getDocumentUseCase: GetDocumentUseCase
    private lateinit var saveScannedDocumentUseCase: SaveScannedDocumentUseCase
    private lateinit var deleteDocumentUseCase: DeleteDocumentUseCase
    private lateinit var shareDocumentUseCase: ShareDocumentUseCase
    private lateinit var exportDocumentUseCase: ExportDocumentUseCase
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

    private fun createViewModel(
        documents: kotlinx.coroutines.flow.Flow<List<ScannedDocument>> = flowOf(emptyList())
    ): HomeViewModel {
        scannerManager = ScannerManager()
        observeDocumentsUseCase = mockk()
        processDocumentsUseCase = mockk()
        getDocumentUseCase = mockk()
        saveScannedDocumentUseCase = mockk(relaxed = true)
        deleteDocumentUseCase = mockk(relaxed = true)
        shareDocumentUseCase = mockk(relaxed = true)
        exportDocumentUseCase = mockk()
        updateDocumentFieldsUseCase = mockk(relaxed = true)
        stringProvider = mockk(relaxed = true)
        analyticsHelper = mockk(relaxed = true)

        coEvery { observeDocumentsUseCase() } returns documents
        coEvery { processDocumentsUseCase(any(), any(), any(), any()) } answers { firstArg() }
        every { stringProvider.getError(any<Throwable>()) } returns "Something went wrong"

        return HomeViewModel(
            savedStateHandle = SavedStateHandle(),
            scannerManager = scannerManager,
            observeDocumentsUseCase = observeDocumentsUseCase,
            processDocumentsUseCase = processDocumentsUseCase,
            getDocumentUseCase = getDocumentUseCase,
            saveScannedDocumentUseCase = saveScannedDocumentUseCase,
            deleteDocumentUseCase = deleteDocumentUseCase,
            shareDocumentUseCase = shareDocumentUseCase,
            exportDocumentUseCase = exportDocumentUseCase,
            updateDocumentFieldsUseCase = updateDocumentFieldsUseCase,
            stringProvider = stringProvider,
            analyticsHelper = analyticsHelper,
            defaultDispatcher = testDispatcher,
        )
    }

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

    @Test
    fun `LaunchScanner flips isScanning on immediately and requests a scan`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            val requests = mutableListOf<Unit>()
            backgroundScope.launch { scannerManager.scanRequest.toList(requests) }

            viewModel.onSendIntent(HomeIntent.LaunchScanner)

            // Set synchronously by the intent handler, before the scan request is even sent.
            assertTrue(viewModel.state.value.isScanning)

            advanceUntilIdle()

            assertEquals(1, requests.size)
            verify(exactly = 1) {
                analyticsHelper.logEvent(match { it.type == AnalyticsEvent.Types.SCAN_STARTED })
            }
        }

    @Test
    fun `LaunchScanner while already scanning is a no-op`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            val requests = mutableListOf<Unit>()
            backgroundScope.launch { scannerManager.scanRequest.toList(requests) }

            viewModel.onSendIntent(HomeIntent.LaunchScanner)
            viewModel.onSendIntent(HomeIntent.LaunchScanner)
            advanceUntilIdle()

            assertEquals(1, requests.size)
            verify(exactly = 1) {
                analyticsHelper.logEvent(match { it.type == AnalyticsEvent.Types.SCAN_STARTED })
            }
        }

    @Test
    fun `successful scan result clears isScanning and saves the document`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            val events = mutableListOf<UiEvent>()
            backgroundScope.launch { viewModel.defaultEvents.toList(events) }

            viewModel.onSendIntent(HomeIntent.LaunchScanner)
            advanceUntilIdle()

            val result = RawScanResult(uri = "content://scan", pageCount = 2)
            scannerManager.onScanResult(Result.success(result))
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isScanning)
            coVerify { saveScannedDocumentUseCase(result) }
            assertTrue(
                events.any { it is UiEvent.ShowMessage && it.type == NotificationType.Success }
            )
        }

    @Test
    fun `failed scan result clears isScanning without saving`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onSendIntent(HomeIntent.LaunchScanner)
            advanceUntilIdle()

            scannerManager.onScanResult(Result.failure(RuntimeException("cancelled")))
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isScanning)
            coVerify(exactly = 0) { saveScannedDocumentUseCase(any()) }
        }
}
