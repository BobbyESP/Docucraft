/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.library

import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.domain.notifications.NotificationType
import com.bobbyesp.docucraft.core.domain.usecase.NotifyUserUseCase
import com.bobbyesp.docucraft.feature.docscanner.FakeDocumentStorage
import com.bobbyesp.docucraft.feature.docscanner.FakeDocumentsRepository
import com.bobbyesp.docucraft.feature.docscanner.FakeExternalDocumentAccess
import com.bobbyesp.docucraft.feature.docscanner.FakeLinkedDocumentsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SaveLinkedToLibraryUseCase
import com.bobbyesp.docucraft.feature.docscanner.testDocument
import com.bobbyesp.docucraft.feature.docscanner.testLinkedDocument
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/** The answer to "it is already in your library: save another copy?". */
@OptIn(ExperimentalCoroutinesApi::class)
class SaveCopyToLibraryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val documents =
        FakeDocumentsRepository(
            documents = listOf(testDocument(uuid = "doc-1")),
            linked = listOf(testLinkedDocument(uuid = LINKED)),
        )
    private val linked = FakeLinkedDocumentsRepository()
    private val storage = FakeDocumentStorage()
    private val notifyUser: NotifyUserUseCase = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // The reason the question was asked.
        documents.hashes["doc-1"] = "hash-of-$LINKED"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `saying yes saves it although it is there, says so and closes`() = runTest {
        val viewModel = viewModel()
        val effects = collectEffects(viewModel)

        viewModel.onSendIntent(SaveCopyIntent.Confirm)
        advanceUntilIdle()

        assertEquals(LINKED, linked.kept.single().first)
        assertEquals(listOf(SaveCopyEffect.Close), effects)
        verify { notifyUser(R.string.saved_to_library, type = NotificationType.Success) }
    }

    @Test
    fun `a copy that cannot be made is said, and the question closes all the same`() = runTest {
        storage.pageCount = null
        val viewModel = viewModel()
        val effects = collectEffects(viewModel)

        viewModel.onSendIntent(SaveCopyIntent.Confirm)
        advanceUntilIdle()

        assertTrue(linked.kept.isEmpty())
        assertEquals(listOf(SaveCopyEffect.Close), effects)
        verify { notifyUser(R.string.save_to_library_failed, type = NotificationType.Error) }
    }

    /** Two taps on the button are one answer. */
    @Test
    fun `asking twice saves once`() = runTest {
        val viewModel = viewModel()

        viewModel.onSendIntent(SaveCopyIntent.Confirm)
        viewModel.onSendIntent(SaveCopyIntent.Confirm)
        advanceUntilIdle()

        assertEquals(1, linked.kept.size)
    }

    private fun viewModel() =
        SaveCopyToLibraryViewModel(
            documentUuid = LINKED,
            saveToLibrary =
                SaveLinkedToLibraryUseCase(
                    documents,
                    linked,
                    storage,
                    FakeExternalDocumentAccess(),
                ),
            notifyUser = notifyUser,
        )

    private fun TestScope.collectEffects(
        viewModel: SaveCopyToLibraryViewModel
    ): List<SaveCopyEffect> {
        val effects = mutableListOf<SaveCopyEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.effects.collect { effects += it }
        }
        return effects
    }

    private companion object {
        const val LINKED = "linked-1"
    }
}
