/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.di

import com.bobbyesp.docucraft.core.navigation.keys.MoveToFolder
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.folders.FolderContentsViewModel
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.folders.FolderViewModel
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.folders.MoveToFolderViewModel
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.tags.DocumentTagsViewModel
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.tags.TagsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The ViewModels of folders and tags.
 *
 * Written as `viewModel { }` lambdas rather than `viewModelOf(::…)`, so that a ViewModel's
 * dispatcher falls back to its Kotlin default instead of needing a binding in the graph.
 */
val organizationViewModels = module {
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

/**
 * A folder, or none: the root, or a folder that does not exist yet. Wrapped because a `null` is not
 * a parameter Koin can tell from a missing one.
 */
data class FolderRef(val uuid: String?)

/** A tag, or none: every tag, or a tag that does not exist yet. */
data class TagRef(val uuid: String?)
