/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.di

import com.bobbyesp.docucraft.feature.docscanner.navigation.MoveToFolder
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.bin.BinViewModel
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.folders.FolderContentsViewModel
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.folders.FolderViewModel
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.folders.MoveToFolderViewModel
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.actions.DocumentActionsViewModel
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.viewmodel.HomeViewModel
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.search.DocumentSearchViewModel
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.tags.DocumentTagsViewModel
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.tags.TagsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

// A manual `viewModel { }` lambda instead of `viewModelOf(::HomeViewModel)` so HomeViewModel's
// `defaultDispatcher` constructor parameter falls back to its Kotlin default (Dispatchers.Default)
// rather than requiring a CoroutineDispatcher binding in the Koin graph.
val documentScannerViewModels = module {
    viewModel {
        HomeViewModel(
            savedStateHandle = get(),
            documentScanner = get(),
            scanRequests = get(),
            observeDocumentsUseCase = get(),
            observeRecentDocumentsUseCase = get(),
            observeLibraryUseCase = get(),
            observeHomeSectionsUseCase = get(),
            tags = get(),
            observeNotFoundDocuments = get(),
            processDocumentsUseCase = get(),
            saveScanDraftUseCase = get(),
            stringProvider = get(),
            analyticsHelper = get(),
        )
    }

    // Manual for the same reason as HomeViewModel: its dispatcher defaults in Kotlin.
    viewModel {
        DocumentSearchViewModel(
            savedStateHandle = get(),
            observeDocumentsUseCase = get(),
            searchDocumentsUseCase = get(),
            observeNotFoundDocuments = get(),
            stringProvider = get(),
            analyticsHelper = get(),
        )
    }

    // Scoped to the navigation entry acting on the document, so the uuid comes from the key rather
    // than from a graph binding.
    viewModel { (documentUuid: String) ->
        DocumentActionsViewModel(
            documentUuid = documentUuid,
            observeDocument = get(),
            moveDocumentToBin = get(),
            updateDocumentFieldsUseCase = get(),
            documentSharer = get(),
            documentExporter = get(),
            forgetLinkedDocument = get(),
            setTextRecognition = get(),
            setFavorite = get(),
            stringProvider = get(),
            analyticsHelper = get(),
        )
    }

    // Manual for the same reason as HomeViewModel: its dispatcher defaults in Kotlin.
    viewModel { (folder: FolderRef) ->
        FolderContentsViewModel(
            folderUuid = folder.uuid,
            folders = get(),
            processDocuments = get(),
            observeNotFoundDocuments = get(),
        )
    }

    viewModel { (folder: FolderRef, parent: FolderRef) ->
        FolderViewModel(
            folderUuid = folder.uuid,
            parentUuid = parent.uuid,
            folders = get(),
            saveFolder = get(),
            stringProvider = get(),
        )
    }

    viewModel { (key: MoveToFolder) ->
        MoveToFolderViewModel(
            documentUuid = key.documentUuid,
            movedFolderUuid = key.folderUuid,
            savedStateHandle = get(),
            folders = get(),
            stringProvider = get(),
        )
    }

    viewModel { (documentUuid: String) ->
        DocumentTagsViewModel(documentUuid = documentUuid, tags = get(), tagByName = get())
    }

    viewModel { (document: BinDocumentRef) ->
        BinViewModel(
            documentUuid = document.uuid,
            documents = get(),
            restoreDocument = get(),
            deleteFromBin = get(),
            emptyBin = get(),
            stringProvider = get(),
        )
    }

    viewModel { (tag: TagRef) ->
        TagsViewModel(
            tagUuid = tag.uuid,
            tags = get(),
            saveTag = get(),
            arrangeHomeSections = get(),
            stringProvider = get(),
        )
    }
}

/** A document of the bin, or none: the bin as a whole. */
data class BinDocumentRef(val uuid: String?)

/**
 * A folder, or none: the root, or a folder that does not exist yet. Wrapped because a `null` is not
 * a parameter Koin can tell from a missing one.
 */
data class FolderRef(val uuid: String?)

/** A tag, or none: every tag, or a tag that does not exist yet. */
data class TagRef(val uuid: String?)
