/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.tags

import com.bobbyesp.docucraft.core.domain.StringProvider
import com.bobbyesp.docucraft.feature.docscanner.FakeTagsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.model.LabelColor
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Tag
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ArrangeHomeSectionsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SaveTagUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.TagDocumentByNameUseCase
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.folders.NameError
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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
class TagsViewModelsTest {

    private val testDispatcher = StandardTestDispatcher()
    private val stringProvider: StringProvider = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun tag(uuid: String, homePosition: Int? = null) =
        Tag(uuid = uuid, name = uuid, color = null, homePosition = homePosition)

    private fun tagsViewModel(tags: FakeTagsRepository, tagUuid: String? = null) =
        TagsViewModel(
            tagUuid = tagUuid,
            tags = tags,
            saveTag = SaveTagUseCase(tags, newUuid = { "new" }),
            arrangeHomeSections = ArrangeHomeSectionsUseCase(tags),
            stringProvider = stringProvider,
        )

    @Test
    fun `the tags of a document are checked, and a tap puts one on or takes it off`() =
        runTest(testDispatcher) {
            val tags =
                FakeTagsRepository(
                    tags = listOf(tag("a"), tag("b")),
                    assignments = mapOf("doc" to setOf("a")),
                )
            val viewModel =
                DocumentTagsViewModel("doc", tags, TagDocumentByNameUseCase(tags, { "new" }))
            advanceUntilIdle()
            assertEquals(setOf("a"), viewModel.state.value.assigned)

            viewModel.onSendIntent(DocumentTagsIntent.Toggle("b"))
            viewModel.onSendIntent(DocumentTagsIntent.Toggle("a"))
            advanceUntilIdle()

            assertEquals(setOf("b"), viewModel.state.value.assigned)
        }

    @Test
    fun `a name that no tag has creates one on the document`() =
        runTest(testDispatcher) {
            val tags = FakeTagsRepository()
            val viewModel =
                DocumentTagsViewModel("doc", tags, TagDocumentByNameUseCase(tags, { "new" }))

            viewModel.onSendIntent(DocumentTagsIntent.AddByName("Invoices"))
            advanceUntilIdle()

            assertEquals(listOf("Invoices"), viewModel.state.value.tags.map { it.name })
            assertEquals(setOf("new"), viewModel.state.value.assigned)
        }

    @Test
    fun `the tags are listed in two groups, Home's in Home's order`() =
        runTest(testDispatcher) {
            val tags =
                FakeTagsRepository(
                    tags = listOf(tag("z", homePosition = 0), tag("m"), tag("a", homePosition = 1))
                )
            val viewModel = tagsViewModel(tags)
            advanceUntilIdle()

            assertEquals(listOf("z", "a"), viewModel.state.value.homeSections.map { it.uuid })
            assertEquals(listOf("m"), viewModel.state.value.otherTags.map { it.uuid })

            viewModel.onSendIntent(TagsIntent.MoveSection("a", by = -1))
            viewModel.onSendIntent(TagsIntent.SetShownInHome("m", shown = true))
            advanceUntilIdle()

            assertEquals(listOf("a", "z", "m"), viewModel.state.value.homeSections.map { it.uuid })
        }

    @Test
    fun `a name another tag has is said beside the field`() =
        runTest(testDispatcher) {
            val tags = FakeTagsRepository(tags = listOf(tag("Invoices")))
            val viewModel = tagsViewModel(tags)
            advanceUntilIdle()

            viewModel.onSendIntent(TagsIntent.Save("invoices", LabelColor.RED))
            advanceUntilIdle()

            assertEquals(NameError.Taken, viewModel.state.value.nameError)
        }

    @Test
    fun `deleting a tag closes every overlay standing on it`() =
        runTest(testDispatcher) {
            val tags = FakeTagsRepository(tags = listOf(tag("a")))
            val viewModel = tagsViewModel(tags, tagUuid = "a")
            val effects = mutableListOf<TagsEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.effects.toList(effects)
            }
            advanceUntilIdle()

            viewModel.onSendIntent(TagsIntent.ConfirmDelete)
            advanceUntilIdle()

            assertTrue(tags.tags.value.isEmpty())
            assertEquals(listOf<TagsEffect>(TagsEffect.CloseAll), effects)
        }
}
