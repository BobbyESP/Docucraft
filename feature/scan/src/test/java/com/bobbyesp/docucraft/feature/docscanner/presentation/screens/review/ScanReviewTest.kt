/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.review

import com.bobbyesp.docucraft.feature.docscanner.FakeDocumentsRepository
import com.bobbyesp.docucraft.feature.docscanner.FakeFoldersRepository
import com.bobbyesp.docucraft.feature.docscanner.FakePagesRepository
import com.bobbyesp.docucraft.feature.docscanner.FakeTagsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.indexing.DocumentIndexQueue
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.PageTextRecord
import com.bobbyesp.docucraft.feature.docscanner.domain.suggestions.DocumentSuggester
import com.bobbyesp.docucraft.feature.docscanner.domain.suggestions.DocumentSuggestions
import com.bobbyesp.docucraft.feature.docscanner.domain.suggestions.SuggestDocumentDetailsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.suggestions.SuggestionOutcome
import com.bobbyesp.docucraft.feature.docscanner.domain.suggestions.SuggestionRequest
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SetDocumentTextRecognitionUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.TagDocumentByNameUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.UpdateDocumentFieldsUseCase
import com.bobbyesp.docucraft.feature.docscanner.testDocument
import com.bobbyesp.docucraft.feature.docscanner.testFolder
import com.bobbyesp.documentcontent.ContentOrigin
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The review of a saved scan, and the suggestions it is ready to show. No build makes suggestions
 * yet, so a suggester that says what it is told stands in for one: what is tested is everything
 * around it, which is what a real one will be dropped into.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScanReviewTest {

    private val testDispatcher = StandardTestDispatcher()

    private val pages = FakePagesRepository()
    private val folders = FakeFoldersRepository(folders = listOf(testFolder("bills", "Bills")))
    private val tags = FakeTagsRepository()
    private val queue =
        object : DocumentIndexQueue {
            override fun enqueue(documentUuid: String) = Unit
        }

    /** What the stand-in suggester was asked, in order. */
    private val asked = mutableListOf<SuggestionRequest>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun document(ocrEnabled: Boolean = false): Document.Managed =
        testDocument(uuid = "scan").copy(ocrEnabled = ocrEnabled)

    private fun text(page: Int, text: String) {
        pages.texts["scan" to page] =
            PageTextRecord(
                text = text,
                origin = ContentOrigin.RECOGNIZED,
                confidence = null,
                engine = null,
            )
    }

    private fun suggester(suggestions: DocumentSuggestions) = DocumentSuggester { request ->
        asked += request
        suggestions
    }

    private fun suggest(documents: FakeDocumentsRepository, suggester: DocumentSuggester?) =
        SuggestDocumentDetailsUseCase(suggester, documents, pages)

    private fun viewModel(
        documents: FakeDocumentsRepository,
        suggester: DocumentSuggester? = null,
    ) =
        ScanReviewViewModel(
            documentUuid = "scan",
            observeDocument = ObserveDocumentUseCase(documents),
            folders = folders,
            tags = tags,
            updateDocumentFields = UpdateDocumentFieldsUseCase(documents),
            setTextRecognition = SetDocumentTextRecognitionUseCase(pages, queue),
            tagByName = TagDocumentByNameUseCase(tags, newUuid = { "new-tag" }),
            suggestDetails = suggest(documents, suggester),
        )

    private fun TestScope.effectsOf(viewModel: ScanReviewViewModel): List<ScanReviewEffect> {
        val effects = mutableListOf<ScanReviewEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.effects.toList(effects)
        }
        return effects
    }

    // ---------------- suggestions ----------------

    @Test
    fun `a build without a suggester has nothing to say`() = runTest {
        val documents = FakeDocumentsRepository(listOf(document()))

        assertEquals(SuggestionOutcome.NotAvailable, suggest(documents, suggester = null)("scan"))
    }

    // What the user is told instead of a suggestion: a scan has no text until it is recognized.
    @Test
    fun `a scan without text recognition gets no suggestions, and the suggester is not asked`() =
        runTest {
            val documents = FakeDocumentsRepository(listOf(document(ocrEnabled = false)))

            val outcome = suggest(documents, suggester(DocumentSuggestions(title = "x")))("scan")

            assertEquals(SuggestionOutcome.TextRecognitionOff, outcome)
            assertTrue(asked.isEmpty())
        }

    @Test
    fun `a scan that was read and says nothing gets no suggestions`() = runTest {
        val documents = FakeDocumentsRepository(listOf(document(ocrEnabled = true)))

        val outcome = suggest(documents, suggester(DocumentSuggestions(title = "x")))("scan")

        assertEquals(SuggestionOutcome.NoText, outcome)
    }

    @Test
    fun `suggestions are made from the text of the pages, in order`() = runTest {
        val documents = FakeDocumentsRepository(listOf(document(ocrEnabled = true)))
        text(page = 1, text = "Total: 750")
        text(page = 0, text = "Rent receipt")
        val suggested = DocumentSuggestions(title = "Rent receipt", tagNames = listOf("Home"))

        val outcome = suggest(documents, suggester(suggested))("scan")

        assertEquals(SuggestionOutcome.Suggested(suggested), outcome)
        assertEquals(listOf("Rent receipt", "Total: 750"), asked.single().pages)
    }

    @Test
    fun `a suggester that fails is an answer, not a crash`() = runTest {
        val documents = FakeDocumentsRepository(listOf(document(ocrEnabled = true)))
        text(page = 0, text = "Rent receipt")

        val outcome = suggest(documents, DocumentSuggester { error("offline") })("scan")

        assertEquals(SuggestionOutcome.Failed, outcome)
    }

    // ---------------- the review ----------------

    @Test
    fun `the review shows the scan and says nothing of suggestions where there are none`() =
        runTest(testDispatcher) {
            val documents = FakeDocumentsRepository(listOf(document()))
            folders.documents.value = listOf(document())
            folders.documentFolders.value = mapOf("scan" to "bills")

            val viewModel = viewModel(documents)
            advanceUntilIdle()

            assertEquals("scan", viewModel.state.value.document?.uuid)
            assertEquals("Bills", viewModel.state.value.folder?.name)
            assertEquals(SuggestionsUiState.Hidden, viewModel.state.value.suggestions)
        }

    @Test
    fun `saving keeps what was typed and closes the review`() =
        runTest(testDispatcher) {
            val documents = FakeDocumentsRepository(listOf(document()))
            val viewModel = viewModel(documents)
            val effects = effectsOf(viewModel)
            advanceUntilIdle()

            viewModel.onSendIntent(ScanReviewIntent.Save(title = " Rent ", description = "  "))
            advanceUntilIdle()

            val saved = documents.documents.value.single()
            assertEquals("Rent", saved.title)
            assertNull(saved.description)
            assertEquals(listOf<ScanReviewEffect>(ScanReviewEffect.Close), effects)
        }

    @Test
    fun `with a suggester and recognition off, the review says why nothing is suggested`() =
        runTest(testDispatcher) {
            val documents = FakeDocumentsRepository(listOf(document(ocrEnabled = false)))

            val viewModel = viewModel(documents, suggester(DocumentSuggestions(title = "x")))
            advanceUntilIdle()

            assertEquals(SuggestionsUiState.TextRecognitionOff, viewModel.state.value.suggestions)
        }

    @Test
    fun `suggestions are shown with their folder, and their parts are taken one by one`() =
        runTest(testDispatcher) {
            val documents = FakeDocumentsRepository(listOf(document(ocrEnabled = true)))
            text(page = 0, text = "Rent receipt")
            val suggested =
                DocumentSuggestions(
                    title = "Rent receipt",
                    folderUuid = "bills",
                    tagNames = listOf("Home"),
                )

            val viewModel = viewModel(documents, suggester(suggested))
            advanceUntilIdle()

            val shown = viewModel.state.value.suggestions as SuggestionsUiState.Ready
            assertEquals(suggested, shown.suggestions)
            assertEquals("Bills", shown.folder?.name)

            viewModel.onSendIntent(ScanReviewIntent.AddSuggestedTag("Home"))
            viewModel.onSendIntent(ScanReviewIntent.MoveToSuggestedFolder)
            advanceUntilIdle()

            assertEquals(setOf("new-tag"), tags.assignments.value["scan"])
            assertEquals("bills", folders.documentFolders.value["scan"])
        }

    @Test
    fun `a scan deleted while it is reviewed closes the review`() =
        runTest(testDispatcher) {
            val documents = FakeDocumentsRepository(listOf(document()))
            val viewModel = viewModel(documents)
            val effects = effectsOf(viewModel)
            advanceUntilIdle()

            documents.moveToBin("scan")
            advanceUntilIdle()

            assertEquals(listOf<ScanReviewEffect>(ScanReviewEffect.Close), effects)
        }
}
