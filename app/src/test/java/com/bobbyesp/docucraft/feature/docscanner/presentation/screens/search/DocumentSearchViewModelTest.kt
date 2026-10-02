/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.search

import androidx.lifecycle.SavedStateHandle
import com.bobbyesp.docucraft.core.domain.StringProvider
import com.bobbyesp.docucraft.core.domain.analytics.AnalyticsEvent
import com.bobbyesp.docucraft.core.domain.repository.AnalyticsHelper
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ProcessDocumentsUseCase
import com.bobbyesp.scanner.ContentRef
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
 * The search screen's state holder: what an empty query answers, when "no matches" may be said, and
 * that a query outlives the process.
 *
 * The matching itself is [ProcessDocumentsUseCase]'s and is not retested here; the fake below
 * matches on the title, which is all these tests need.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DocumentSearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val analyticsHelper: AnalyticsHelper = mockk(relaxed = true)
    private val processDocumentsUseCase: ProcessDocumentsUseCase = mockk()
    private val documents = MutableStateFlow(listOf(document("Invoice"), document("Notes")))

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { processDocumentsUseCase(any(), any(), any(), any()) } answers
            {
                val docs = firstArg<List<Document>>()
                val query = secondArg<String>()
                docs.filter { it.title.orEmpty().contains(query, ignoreCase = true) }
            }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun document(title: String) =
        Document(
            uuid = title.lowercase(),
            filename = "$title.pdf",
            title = title,
            description = null,
            location = ContentRef("content://stored/$title.pdf"),
            capturedAtEpochMillis = 1_000L,
            sizeBytes = 2_048L,
            pageCount = 1,
            thumbnail = null,
        )

    private fun createViewModel(savedState: SavedStateHandle = SavedStateHandle()) =
        DocumentSearchViewModel(
            savedStateHandle = savedState,
            observeDocumentsUseCase =
                mockk<ObserveDocumentsUseCase>().also { every { it() } returns documents },
            processDocumentsUseCase = processDocumentsUseCase,
            stringProvider = mockk<StringProvider>(relaxed = true),
            analyticsHelper = analyticsHelper,
            defaultDispatcher = testDispatcher,
        )

    /** Home lists everything; search with nothing typed shows what it is for instead. */
    @Test
    fun `an empty query has no results and is not searched`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state.results.isEmpty())
            assertFalse(state.hasNoMatches)
            coVerify(exactly = 0) { processDocumentsUseCase(any(), any(), any(), any()) }
        }

    @Test
    fun `a query finds its documents`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onSendIntent(DocumentSearchIntent.UpdateQuery("inv"))
            advanceUntilIdle()

            assertEquals(listOf("invoice"), viewModel.state.value.results.map { it.uuid })
            assertEquals("inv", viewModel.state.value.resultsFor)
        }

    /**
     * Saying "no matches" before the results for this query are in would flash it at every
     * keystroke, before the debounce has even let the search run.
     */
    @Test
    fun `no matches is only said once this query has been searched`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onSendIntent(DocumentSearchIntent.UpdateQuery("zzz"))
            assertFalse(viewModel.state.value.hasNoMatches)

            advanceUntilIdle()
            assertTrue(viewModel.state.value.hasNoMatches)
        }

    /** A document scanned while the results are on screen joins them without retyping. */
    @Test
    fun `results follow the catalogue as it changes`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onSendIntent(DocumentSearchIntent.UpdateQuery("inv"))
            advanceUntilIdle()

            documents.value = documents.value + document("Invoice 2")
            advanceUntilIdle()

            assertEquals(
                listOf("invoice", "invoice 2"),
                viewModel.state.value.results.map { it.uuid },
            )
        }

    @Test
    fun `clearing the query empties the results`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onSendIntent(DocumentSearchIntent.UpdateQuery("inv"))
            advanceUntilIdle()

            viewModel.onSendIntent(DocumentSearchIntent.ClearQuery)
            advanceUntilIdle()

            assertEquals("", viewModel.state.value.query)
            assertTrue(viewModel.state.value.results.isEmpty())
        }

    @Test
    fun `the query survives process death`() =
        runTest(testDispatcher) {
            val savedState = SavedStateHandle()
            createViewModel(savedState).onSendIntent(DocumentSearchIntent.UpdateQuery("notes"))

            val restored = createViewModel(savedState)
            advanceUntilIdle()

            assertEquals("notes", restored.state.value.query)
            assertEquals(listOf("notes"), restored.state.value.results.map { it.uuid })
        }

    /** Counted once a query says something; one or two letters are still being typed. */
    @Test
    fun `only queries of three or more letters are counted as searches`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()

            viewModel.onSendIntent(DocumentSearchIntent.UpdateQuery("in"))
            viewModel.onSendIntent(DocumentSearchIntent.UpdateQuery("inv"))

            verify(exactly = 1) {
                analyticsHelper.logEvent(match { it.type == AnalyticsEvent.Types.SEARCH_PERFORMED })
            }
        }
}
