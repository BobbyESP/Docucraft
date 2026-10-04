/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.folders

import androidx.lifecycle.SavedStateHandle
import com.bobbyesp.docucraft.core.domain.StringProvider
import com.bobbyesp.docucraft.feature.docscanner.FakeDocumentActivityRepository
import com.bobbyesp.docucraft.feature.docscanner.FakeFoldersRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.SortOption
import com.bobbyesp.docucraft.feature.docscanner.domain.model.FolderIcon
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.FolderDepth
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveNotFoundDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ProcessDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SaveFolderUseCase
import com.bobbyesp.docucraft.feature.docscanner.testDocument
import com.bobbyesp.docucraft.feature.docscanner.testFolder
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** What a folder's screen shows, and what its overlays do, over folders held in memory. */
@OptIn(ExperimentalCoroutinesApi::class)
class FolderViewModelsTest {

    private val testDispatcher = StandardTestDispatcher()
    private val stringProvider: StringProvider = mockk(relaxed = true)
    private val activity = FakeDocumentActivityRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun contents(folders: FakeFoldersRepository, folderUuid: String?) =
        FolderContentsViewModel(
            folderUuid = folderUuid,
            folders = folders,
            processDocuments = ProcessDocumentsUseCase(),
            observeNotFoundDocuments = ObserveNotFoundDocumentsUseCase(activity),
            defaultDispatcher = testDispatcher,
        )

    private fun folderViewModel(
        folders: FakeFoldersRepository,
        folderUuid: String?,
        parentUuid: String? = null,
    ) =
        FolderViewModel(
            folderUuid = folderUuid,
            parentUuid = parentUuid,
            folders = folders,
            saveFolder = SaveFolderUseCase(folders, newUuid = { "new" }),
            stringProvider = stringProvider,
        )

    private fun move(
        folders: FakeFoldersRepository,
        documentUuid: String? = null,
        folderUuid: String? = null,
    ) =
        MoveToFolderViewModel(
            documentUuid = documentUuid,
            movedFolderUuid = folderUuid,
            savedStateHandle = SavedStateHandle(),
            folders = folders,
            stringProvider = stringProvider,
        )

    private fun <T> TestScope.collect(flow: kotlinx.coroutines.flow.Flow<T>): List<T> {
        val collected = mutableListOf<T>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.toList(collected) }
        return collected
    }

    // ---------------- contents ----------------

    @Test
    fun `a folder shows its folders, its documents and the way to it`() =
        runTest(testDispatcher) {
            val folders =
                FakeFoldersRepository(
                    folders =
                        listOf(
                            testFolder("home", "Home"),
                            testFolder("taxes", "Taxes", parentUuid = "home"),
                            testFolder("other", "Other"),
                        ),
                    documents = listOf(testDocument("in"), testDocument("out")),
                    documentFolders = mapOf("in" to "home"),
                )

            val viewModel = contents(folders, "home")
            advanceUntilIdle()

            val state = viewModel.state.value
            assertFalse(state.isLoading)
            assertEquals("Home", state.folder?.name)
            assertEquals(listOf("home"), state.path.map { it.uuid })
            assertEquals(listOf("taxes"), state.subfolders.map { it.uuid })
            assertEquals(listOf("in"), state.documents.map { it.uuid })
            assertTrue(state.canCreateFolder)
        }

    @Test
    fun `the root shows the folders without a parent and the documents in no folder`() =
        runTest(testDispatcher) {
            val folders =
                FakeFoldersRepository(
                    folders = listOf(testFolder("a"), testFolder("b", parentUuid = "a")),
                    documents = listOf(testDocument("in"), testDocument("out")),
                    documentFolders = mapOf("in" to "a"),
                )

            val viewModel = contents(folders, null)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state.isRoot)
            assertEquals(listOf("a"), state.subfolders.map { it.uuid })
            assertEquals(listOf("out"), state.documents.map { it.uuid })
        }

    @Test
    fun `a folder remembers the order chosen for it`() =
        runTest(testDispatcher) {
            val folders =
                FakeFoldersRepository(
                    folders = listOf(testFolder("a")),
                    documents =
                        listOf(
                            testDocument("z", title = "Zebra"),
                            testDocument("y", title = "Apple"),
                        ),
                    documentFolders = mapOf("z" to "a", "y" to "a"),
                )
            val viewModel = contents(folders, "a")
            advanceUntilIdle()

            viewModel.onSendIntent(FolderContentsIntent.ApplySort(SortOption.NameAsc))
            advanceUntilIdle()

            assertEquals(SortOption.NameAsc, folders.getFolder("a")?.sort)
            assertEquals(listOf("y", "z"), viewModel.state.value.documents.map { it.uuid })
        }

    @Test
    fun `no folder can be created in one that is as deep as folders go`() =
        runTest(testDispatcher) {
            val chain =
                (1..FolderDepth.MAX).map { depth ->
                    testFolder("f$depth", parentUuid = if (depth == 1) null else "f${depth - 1}")
                }
            val viewModel = contents(FakeFoldersRepository(folders = chain), "f${FolderDepth.MAX}")
            advanceUntilIdle()

            assertFalse(viewModel.state.value.canCreateFolder)
        }

    @Test
    fun `a folder deleted while its screen is open closes it`() =
        runTest(testDispatcher) {
            val folders = FakeFoldersRepository(folders = listOf(testFolder("a")))
            val viewModel = contents(folders, "a")
            val effects = collect(viewModel.effects)
            advanceUntilIdle()

            folders.delete("a")
            advanceUntilIdle()

            assertEquals(listOf<FolderContentsEffect>(FolderContentsEffect.Close), effects)
        }

    // ---------------- one folder ----------------

    @Test
    fun `creating a folder closes the form`() =
        runTest(testDispatcher) {
            val folders = FakeFoldersRepository(folders = listOf(testFolder("parent")))
            val viewModel = folderViewModel(folders, folderUuid = null, parentUuid = "parent")
            val effects = collect(viewModel.effects)

            viewModel.onSendIntent(FolderIntent.Save("Taxes", null, FolderIcon.Default))
            advanceUntilIdle()

            assertEquals("parent", folders.getFolder("new")?.parentUuid)
            assertEquals(listOf<FolderEffect>(FolderEffect.Close), effects)
        }

    @Test
    fun `a name that is taken is said beside the field and the form stays`() =
        runTest(testDispatcher) {
            val folders = FakeFoldersRepository(folders = listOf(testFolder("a", "Taxes")))
            val viewModel = folderViewModel(folders, folderUuid = null)
            val effects = collect(viewModel.effects)

            viewModel.onSendIntent(FolderIntent.Save("taxes", null, FolderIcon.Default))
            advanceUntilIdle()

            assertEquals(NameError.Taken, viewModel.state.value.nameError)
            assertTrue(effects.isEmpty())

            viewModel.onSendIntent(FolderIntent.NameEdited)
            assertNull(viewModel.state.value.nameError)
        }

    @Test
    fun `deleting a folder closes every overlay standing on it`() =
        runTest(testDispatcher) {
            val folders = FakeFoldersRepository(folders = listOf(testFolder("a")))
            val viewModel = folderViewModel(folders, folderUuid = "a")
            val effects = collect(viewModel.effects)
            advanceUntilIdle()

            viewModel.onSendIntent(FolderIntent.ConfirmDelete(withContents = false))
            advanceUntilIdle()

            assertNull(folders.getFolder("a"))
            assertTrue(folders.binned.isEmpty())
            assertEquals(listOf<FolderEffect>(FolderEffect.CloseAll), effects)
        }

    @Test
    fun `a folder deleted with what it holds sends its documents to the bin`() =
        runTest(testDispatcher) {
            val folders =
                FakeFoldersRepository(
                    folders = listOf(testFolder("a"), testFolder("b", parentUuid = "a")),
                    documents = listOf(testDocument("in"), testDocument("deep")),
                    documentFolders = mapOf("in" to "a", "deep" to "b"),
                )
            val viewModel = folderViewModel(folders, folderUuid = "a")
            advanceUntilIdle()

            viewModel.onSendIntent(FolderIntent.ConfirmDelete(withContents = true))
            advanceUntilIdle()

            assertTrue(folders.folders.value.isEmpty())
            assertEquals(setOf("in", "deep"), folders.binned.toSet())
        }

    @Test
    fun `a document whose file is not there is shown as not found`() =
        runTest(testDispatcher) {
            val folders =
                FakeFoldersRepository(
                    folders = listOf(testFolder("a")),
                    documents = listOf(testDocument("doc")),
                    documentFolders = mapOf("doc" to "a"),
                )
            activity.notFound.value = setOf("doc")

            val viewModel = contents(folders, "a")
            advanceUntilIdle()

            assertEquals(setOf("doc"), viewModel.state.value.notFoundUuids)
        }

    // ---------------- moving ----------------

    @Test
    fun `the picker opens where the document is, and that is not a place to move it to`() =
        runTest(testDispatcher) {
            val folders =
                FakeFoldersRepository(
                    folders = listOf(testFolder("a"), testFolder("b", parentUuid = "a")),
                    documents = listOf(testDocument("doc")),
                    documentFolders = mapOf("doc" to "a"),
                )
            val viewModel = move(folders, documentUuid = "doc")
            advanceUntilIdle()

            assertEquals("a", viewModel.state.value.location?.uuid)
            assertEquals(listOf("b"), viewModel.state.value.subfolders.map { it.uuid })
            assertFalse(viewModel.state.value.canMoveHere)
        }

    @Test
    fun `a document moves to the folder being looked at and the picker closes`() =
        runTest(testDispatcher) {
            val folders =
                FakeFoldersRepository(
                    folders = listOf(testFolder("a")),
                    documents = listOf(testDocument("doc")),
                )
            val viewModel = move(folders, documentUuid = "doc")
            val effects = collect(viewModel.effects)
            advanceUntilIdle()

            viewModel.onSendIntent(MoveToFolderIntent.Browse("a"))
            advanceUntilIdle()
            assertTrue(viewModel.state.value.canMoveHere)

            viewModel.onSendIntent(MoveToFolderIntent.MoveHere)
            advanceUntilIdle()

            assertEquals("a", folders.documentFolders.value["doc"])
            assertEquals(listOf<MoveToFolderEffect>(MoveToFolderEffect.Close), effects)
        }

    @Test
    fun `a folder being moved is not offered as a place to go into`() =
        runTest(testDispatcher) {
            val folders = FakeFoldersRepository(folders = listOf(testFolder("a"), testFolder("b")))
            val viewModel = move(folders, folderUuid = "a")
            advanceUntilIdle()

            assertEquals(listOf("b"), viewModel.state.value.subfolders.map { it.uuid })
        }

    @Test
    fun `a folder moved where its name is taken stays, and so does the picker`() =
        runTest(testDispatcher) {
            val folders =
                FakeFoldersRepository(
                    folders =
                        listOf(
                            testFolder("a", "Taxes"),
                            testFolder("b", "Home"),
                            testFolder("c", "taxes", parentUuid = "b"),
                        )
                )
            val viewModel = move(folders, folderUuid = "a")
            val effects = collect(viewModel.effects)
            advanceUntilIdle()

            viewModel.onSendIntent(MoveToFolderIntent.Browse("b"))
            advanceUntilIdle()
            viewModel.onSendIntent(MoveToFolderIntent.MoveHere)
            advanceUntilIdle()

            assertNull(folders.getFolder("a")?.parentUuid)
            assertTrue(effects.isEmpty())
        }
}
