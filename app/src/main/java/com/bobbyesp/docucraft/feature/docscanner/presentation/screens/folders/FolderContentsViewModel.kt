/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.folders

import com.bobbyesp.docucraft.core.util.viewModel.BaseViewModel
import com.bobbyesp.docucraft.feature.docscanner.domain.FilterOptions
import com.bobbyesp.docucraft.feature.docscanner.domain.SortOption
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Folder
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.FoldersRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.FolderDepth
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ProcessDocumentsUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

sealed interface FolderContentsIntent {
    /** How the documents here are ordered. A folder remembers it; the root does for the session. */
    data class ApplySort(val sort: SortOption) : FolderContentsIntent

    /** Pin the folder to Home, or take it off. */
    data object TogglePinned : FolderContentsIntent
}

sealed interface FolderContentsEffect {
    /** The folder is gone, so there is nothing left to show. */
    data object Close : FolderContentsEffect
}

/**
 * @property isRoot Whether this is the root of the library rather than a folder.
 * @property folder The folder shown. `null` at the root, and for a folder still being read.
 * @property path From the root down to [folder], itself included.
 * @property canCreateFolder Whether a folder can be put here: not when this one is as deep as the
 *   app lets folders go.
 */
data class FolderContentsUiState(
    val isRoot: Boolean,
    val folder: Folder? = null,
    val path: List<Folder> = emptyList(),
    val subfolders: List<Folder> = emptyList(),
    val documents: List<Document.Managed> = emptyList(),
    val sort: SortOption = SortOption.DateDesc,
    val isLoading: Boolean = true,
    val canCreateFolder: Boolean = false,
) {
    val isEmpty: Boolean
        get() = !isLoading && subfolders.isEmpty() && documents.isEmpty()
}

/**
 * What one folder holds, or the root of the library: its folders, and the documents directly in it.
 *
 * Followed rather than read once: folders and documents are moved, renamed and deleted from sheets
 * that open over this screen, and on a wide window from the pane beside it.
 */
class FolderContentsViewModel(
    private val folderUuid: String?,
    private val folders: FoldersRepository,
    private val processDocuments: ProcessDocumentsUseCase,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) :
    BaseViewModel<FolderContentsIntent, FolderContentsUiState, FolderContentsEffect>(
        initialState = FolderContentsUiState(isRoot = folderUuid == null)
    ) {

    /** The root has no folder to remember its order in. */
    private val rootSort = MutableStateFlow(SortOption.DateDesc)

    /** As in the document's actions: `null` is "not read yet" first, and "deleted" afterwards. */
    private var wasLoaded = false

    init {
        launch {
            val folder = if (folderUuid == null) flowOf(null) else folders.observeFolder(folderUuid)
            combine(
                    folder,
                    folders.observeFolders(folderUuid),
                    folders.observeDocuments(folderUuid),
                    rootSort,
                ) { current, subfolders, documents, rootSort ->
                    if (folderUuid != null && current == null) return@combine null

                    val sort = current?.sort ?: rootSort
                    val path = folderUuid?.let { folders.pathTo(it) }.orEmpty()
                    FolderContentsUiState(
                        isRoot = folderUuid == null,
                        folder = current,
                        path = path,
                        subfolders = subfolders,
                        documents =
                            withContext(defaultDispatcher) {
                                processDocuments(documents, FilterOptions.default, sort)
                            },
                        sort = sort,
                        isLoading = false,
                        canCreateFolder = FolderDepth.allowsFolderIn(path),
                    )
                }
                .collect { state ->
                    if (state != null) {
                        wasLoaded = true
                        setState { state }
                    } else if (wasLoaded) {
                        sendEffect(FolderContentsEffect.Close)
                    }
                }
        }
    }

    override fun onHandleIntent(intent: FolderContentsIntent) {
        when (intent) {
            is FolderContentsIntent.ApplySort ->
                if (folderUuid == null) rootSort.value = intent.sort
                else launch { folders.setSort(folderUuid, intent.sort) }

            FolderContentsIntent.TogglePinned -> {
                val folder = currentState.folder ?: return
                launch { folders.setPinned(folder.uuid, !folder.isPinned) }
            }
        }
    }
}
