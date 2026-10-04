/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.folders

import androidx.lifecycle.SavedStateHandle
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.domain.StringProvider
import com.bobbyesp.docucraft.core.domain.notifications.NotificationType
import com.bobbyesp.docucraft.core.util.events.UiEvent
import com.bobbyesp.docucraft.core.util.viewModel.BaseViewModel
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Folder
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.FolderChange
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.FoldersRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.FolderDepth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

sealed interface MoveToFolderIntent {
    /** Look inside a folder, or the root for `null`. */
    data class Browse(val folderUuid: String?) : MoveToFolderIntent

    /** Put the document, or the folder, in the folder being looked at. */
    data object MoveHere : MoveToFolderIntent
}

sealed interface MoveToFolderEffect {
    data object Close : MoveToFolderEffect
}

/**
 * @property path From the root down to the folder being looked at. Empty at the root.
 * @property subfolders The folders to go into from here. Never the folder being moved: a folder
 *   cannot go inside itself, so there is nothing to choose in it.
 * @property canMoveHere Whether confirming would change anything, and is allowed.
 * @property canCreateFolder Whether a folder can be created where the user is looking.
 */
data class MoveToFolderUiState(
    val path: List<Folder> = emptyList(),
    val subfolders: List<Folder> = emptyList(),
    val isLoading: Boolean = true,
    val canMoveHere: Boolean = false,
    val canCreateFolder: Boolean = false,
) {
    /** The folder being looked at, or `null` at the root. */
    val location: Folder?
        get() = path.lastOrNull()
}

/**
 * Choosing where a document, or a folder, goes. The folders are walked inside the sheet: where the
 * user is looking is this sheet's state and is discarded with it, not a place to come back to.
 *
 * @param documentUuid The document to move, or `null` when it is [movedFolderUuid] that moves.
 */
class MoveToFolderViewModel(
    private val documentUuid: String?,
    private val movedFolderUuid: String?,
    private val savedStateHandle: SavedStateHandle,
    private val folders: FoldersRepository,
    private val stringProvider: StringProvider,
) :
    BaseViewModel<MoveToFolderIntent, MoveToFolderUiState, MoveToFolderEffect>(
        initialState = MoveToFolderUiState()
    ) {

    /** Where the document or the folder is now: moving it there would change nothing. */
    private var origin: String? = null

    init {
        launch { browseFromWhereItIs() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun browseFromWhereItIs() {
        origin =
            when {
                documentUuid != null -> folders.observeFolderOf(documentUuid).first()?.uuid
                movedFolderUuid != null -> folders.getFolder(movedFolderUuid)?.parentUuid
                else -> null
            }
        // Starts where the thing is, so that the folders beside it are one tap away. After a
        // process death, where the user had got to.
        if (!savedStateHandle.contains(KEY_BROWSING)) savedStateHandle[KEY_BROWSING] = origin

        savedStateHandle
            .getStateFlow<String?>(KEY_BROWSING, origin)
            .flatMapLatest { browsing ->
                folders.observeFolders(browsing).map { subfolders -> browsing to subfolders }
            }
            .collect { (browsing, subfolders) ->
                val path = browsing?.let { folders.pathTo(it) }.orEmpty()
                // The folder that was being looked at was deleted meanwhile: back to the root.
                if (browsing != null && path.isEmpty()) {
                    savedStateHandle[KEY_BROWSING] = null
                    return@collect
                }
                setState {
                    copy(
                        path = path,
                        subfolders = subfolders.filterNot { it.uuid == movedFolderUuid },
                        isLoading = false,
                        canMoveHere = browsing != origin && allows(path),
                        canCreateFolder = FolderDepth.allowsFolderIn(path),
                    )
                }
            }
    }

    /** A document goes anywhere. A folder only where a folder can be created. */
    private fun allows(path: List<Folder>): Boolean =
        movedFolderUuid == null || FolderDepth.allowsFolderIn(path)

    override fun onHandleIntent(intent: MoveToFolderIntent) {
        when (intent) {
            is MoveToFolderIntent.Browse -> savedStateHandle[KEY_BROWSING] = intent.folderUuid
            MoveToFolderIntent.MoveHere -> move()
        }
    }

    private fun move() = launch {
        val destination = currentState.location
        val change =
            when {
                documentUuid != null ->
                    folders.moveDocuments(listOf(documentUuid), destination?.uuid)
                movedFolderUuid != null -> folders.move(movedFolderUuid, destination?.uuid)
                else -> return@launch
            }

        when (change) {
            FolderChange.Done -> {
                val message =
                    if (destination == null) stringProvider.get(R.string.moved_to_library)
                    else stringProvider.get(R.string.moved_to_folder, destination.name)
                sendUiEvent(UiEvent.ShowMessage(message, NotificationType.Success))
                sendEffect(MoveToFolderEffect.Close)
            }
            // The sheet stays: another folder can still be chosen.
            FolderChange.NameTaken -> say(R.string.folder_name_taken_there)
            FolderChange.WouldContainItself -> say(R.string.folder_cannot_contain_itself)
            FolderChange.NotFound,
            FolderChange.NameEmpty -> say(R.string.folder_not_found)
        }
    }

    private fun say(message: Int) {
        sendUiEvent(UiEvent.ShowMessage(stringProvider.get(message), NotificationType.Error))
    }

    private companion object {
        const val KEY_BROWSING = "browsing_folder"
    }
}
