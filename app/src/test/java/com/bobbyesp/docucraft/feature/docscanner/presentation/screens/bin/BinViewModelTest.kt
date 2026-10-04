/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.bin

import com.bobbyesp.docucraft.core.domain.StringProvider
import com.bobbyesp.docucraft.feature.docscanner.FakeDocumentStorage
import com.bobbyesp.docucraft.feature.docscanner.FakeDocumentThumbnails
import com.bobbyesp.docucraft.feature.docscanner.FakeDocumentsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.indexing.DocumentIndexQueue
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.DeleteFromBinUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.EmptyBinUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.RestoreDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.testDocument
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BinViewModelTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    private val testDispatcher = StandardTestDispatcher()
    private val stringProvider: StringProvider = mockk(relaxed = true)
    private val queue =
        object : DocumentIndexQueue {
            override fun enqueue(documentUuid: String) = Unit
        }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun binned(uuid: String, daysAgo: Int) =
        testDocument(uuid = uuid).copy(trashedAtEpochMillis = now - daysAgo * day)

    private fun viewModel(documents: FakeDocumentsRepository, documentUuid: String? = null) =
        DeleteFromBinUseCase(documents, FakeDocumentStorage(), FakeDocumentThumbnails()).let {
            deleteFromBin ->
            BinViewModel(
                documentUuid = documentUuid,
                documents = documents,
                restoreDocument = RestoreDocumentUseCase(documents, queue),
                deleteFromBin = deleteFromBin,
                emptyBin = EmptyBinUseCase(documents, deleteFromBin),
                stringProvider = stringProvider,
                now = { now },
            )
        }

    private fun TestScope.effectsOf(viewModel: BinViewModel): List<BinEffect> {
        val effects = mutableListOf<BinEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.effects.toList(effects)
        }
        return effects
    }

    @Test
    fun `the bin lists what was deleted, last first, with the days each has left`() =
        runTest(testDispatcher) {
            val documents =
                FakeDocumentsRepository(
                    listOf(testDocument("kept"), binned("old", daysAgo = 29), binned("new", 0))
                )

            val viewModel = viewModel(documents)
            advanceUntilIdle()

            val listed = viewModel.state.value.documents
            assertEquals(listOf("new", "old"), listed.map { it.document.uuid })
            assertEquals(listOf(30, 1), listed.map { it.daysLeft })
        }

    @Test
    fun `restoring a document closes what was open on it`() =
        runTest(testDispatcher) {
            val documents = FakeDocumentsRepository(listOf(binned("a", daysAgo = 1)))
            val viewModel = viewModel(documents, documentUuid = "a")
            val effects = effectsOf(viewModel)
            advanceUntilIdle()

            viewModel.onSendIntent(BinIntent.Restore)
            advanceUntilIdle()

            assertEquals(null, documents.documents.value.single().trashedAtEpochMillis)
            assertEquals(listOf<BinEffect>(BinEffect.CloseAll), effects)
        }

    @Test
    fun `deleting a document for good closes what was open on it`() =
        runTest(testDispatcher) {
            val documents = FakeDocumentsRepository(listOf(binned("a", daysAgo = 1)))
            val viewModel = viewModel(documents, documentUuid = "a")
            val effects = effectsOf(viewModel)
            advanceUntilIdle()

            viewModel.onSendIntent(BinIntent.ConfirmDeleteForever)
            advanceUntilIdle()

            assertTrue(documents.documents.value.isEmpty())
            assertEquals(listOf<BinEffect>(BinEffect.CloseAll), effects)
        }

    @Test
    fun `emptying the bin leaves the library alone and closes the confirmation`() =
        runTest(testDispatcher) {
            val documents =
                FakeDocumentsRepository(listOf(testDocument("kept"), binned("a", daysAgo = 1)))
            val viewModel = viewModel(documents)
            val effects = effectsOf(viewModel)
            advanceUntilIdle()

            viewModel.onSendIntent(BinIntent.ConfirmEmpty)
            advanceUntilIdle()

            assertEquals(listOf("kept"), documents.documents.value.map { it.uuid })
            assertTrue(viewModel.state.value.isEmpty)
            assertEquals(listOf<BinEffect>(BinEffect.Close), effects)
        }
}
