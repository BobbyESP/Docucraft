/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.folders

import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.domain.StringProvider
import com.bobbyesp.docucraft.core.domain.notifications.NotificationType
import com.bobbyesp.docucraft.core.util.events.UiEvent
import com.bobbyesp.docucraft.core.util.viewModel.BaseViewModel
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Folder
import com.bobbyesp.docucraft.feature.docscanner.domain.model.FolderIcon
import com.bobbyesp.docucraft.feature.docscanner.domain.model.LabelColor
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.FolderChange
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.FoldersRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SaveFolderUseCase

sealed interface FolderIntent {
    /** Create the folder, or change it, as the form now says. */
    data class Save(val name: String, val color: LabelColor?, val icon: FolderIcon) : FolderIntent

    /** The name was typed in again, so what was wrong with the last one no longer applies. */
    data object NameEdited : FolderIntent

    data class SetPinned(val pinned: Boolean) : FolderIntent

    /**
     * Delete the folder. What it holds goes to the folder it was in, unless the user asked for it
     * to go too: then the folders inside it are deleted with it, and their documents go to the bin.
     */
    data class ConfirmDelete(val withContents: Boolean) : FolderIntent
}

sealed interface FolderEffect {
    /** The change is made and this overlay has nothing left to show. */
    data object Close : FolderEffect

    /** The folder is gone, so every overlay standing on it must go too. */
    data object CloseAll : FolderEffect
}

/** Why a name was not taken. Each is something the user typed, so each is said beside the field. */
enum class NameError {
    Empty,
    Taken,
}

/**
 * @property folder The folder acted on. `null` for a folder that is being created, and for one
 *   still being read.
 * @property isNew Whether the folder does not exist yet.
 */
data class FolderUiState(
    val folder: Folder? = null,
    val isNew: Boolean,
    val isLoading: Boolean = !isNew,
    val nameError: NameError? = null,
)

/**
 * What can be done to one folder, scoped to the navigation entry doing it: creating or editing it,
 * pinning it, deleting it. One per overlay, as the document's actions are.
 *
 * @param folderUuid The folder, or `null` to create one in [parentUuid].
 */
class FolderViewModel(
    private val folderUuid: String?,
    private val parentUuid: String?,
    private val folders: FoldersRepository,
    private val saveFolder: SaveFolderUseCase,
    private val stringProvider: StringProvider,
) :
    BaseViewModel<FolderIntent, FolderUiState, FolderEffect>(
        initialState = FolderUiState(isNew = folderUuid == null)
    ) {

    private var wasLoaded = false

    init {
        if (folderUuid != null) {
            launch {
                folders.observeFolder(folderUuid).collect { found ->
                    setState { copy(folder = found, isLoading = false) }
                    if (found != null) wasLoaded = true
                    else if (wasLoaded) sendEffect(FolderEffect.CloseAll)
                }
            }
        }
    }

    override fun onHandleIntent(intent: FolderIntent) {
        when (intent) {
            is FolderIntent.Save -> save(intent)
            FolderIntent.NameEdited -> setState { copy(nameError = null) }
            is FolderIntent.SetPinned -> setPinned(intent.pinned)
            is FolderIntent.ConfirmDelete -> delete(intent.withContents)
        }
    }

    private fun save(intent: FolderIntent.Save) = launch {
        when (saveFolder(folderUuid, parentUuid, intent.name, intent.color, intent.icon)) {
            FolderChange.Done -> sendEffect(FolderEffect.Close)
            FolderChange.NameEmpty -> setState { copy(nameError = NameError.Empty) }
            FolderChange.NameTaken -> setState { copy(nameError = NameError.Taken) }
            // Where it was going is gone, or the folder itself is: nothing here can be fixed.
            FolderChange.NotFound,
            FolderChange.WouldContainItself -> {
                say(R.string.folder_not_found, NotificationType.Error)
                sendEffect(FolderEffect.Close)
            }
        }
    }

    private fun setPinned(pinned: Boolean) = launch {
        val folder = currentState.folder ?: return@launch
        folders.setPinned(folder.uuid, pinned)
        say(
            if (pinned) R.string.folder_pinned else R.string.folder_unpinned,
            NotificationType.Success,
        )
    }

    /** Closing is left to the folder disappearing, which the observer above notices. */
    private fun delete(withContents: Boolean) = launch {
        val folder = currentState.folder ?: return@launch
        if (withContents) {
            folders.deleteWithContents(folder.uuid)
            say(R.string.folder_deleted_with_contents, NotificationType.Success)
        } else {
            folders.delete(folder.uuid)
            say(R.string.folder_deleted, NotificationType.Success)
        }
    }

    private fun say(message: Int, type: NotificationType) {
        sendUiEvent(UiEvent.ShowMessage(stringProvider.get(message), type))
    }
}
