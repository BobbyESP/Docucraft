/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.folders

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.docucraft.core.presentation.navigation.Navigator
import com.bobbyesp.docucraft.core.presentation.navigation.overlay.LocalOverlayContext
import com.bobbyesp.docucraft.core.presentation.navigation.overlay.OverlayPreference
import com.bobbyesp.docucraft.core.presentation.navigation.overlay.OverlaySceneStrategy
import com.bobbyesp.docucraft.feature.docscanner.di.FolderRef
import com.bobbyesp.docucraft.feature.docscanner.domain.model.FolderIcon
import com.bobbyesp.docucraft.feature.docscanner.domain.model.LabelColor
import com.bobbyesp.docucraft.feature.docscanner.navigation.DeleteFolder
import com.bobbyesp.docucraft.feature.docscanner.navigation.DocumentActions
import com.bobbyesp.docucraft.feature.docscanner.navigation.FolderActions
import com.bobbyesp.docucraft.feature.docscanner.navigation.FolderContents
import com.bobbyesp.docucraft.feature.docscanner.navigation.FolderEditor
import com.bobbyesp.docucraft.feature.docscanner.navigation.MoveToFolder
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.organization.ShowMessages
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.NoDocumentOpenPane
import com.bobbyesp.docucraft.feature.pdfviewer.navigation.PdfViewer
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The folders of the library: what one holds, and everything done to one.
 *
 * A folder's contents are a list like Home, and on wide windows take its place beside the open
 * document. Everything else is an overlay over whatever is showing, declared as an ordinary
 * destination and left to [OverlaySceneStrategy] to place.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.foldersSection(navigator: Navigator, selectedDocumentId: String?) {
    entry<FolderContents>(
        metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { NoDocumentOpenPane() })
    ) { key ->
        val viewModel: FolderContentsViewModel = koinViewModel {
            parametersOf(FolderRef(key.folderUuid))
        }

        FolderContentsScreen(
            viewModel = viewModel,
            navigation =
                FolderContentsNavigation(
                    onBack = navigator::goBack,
                    onOpenDocument = { uuid ->
                        // As from search: a document replaces the one open beside the list, and
                        // the folder stays, so back returns to it.
                        navigator.goBackWhile { it is PdfViewer }
                        navigator.goTo(PdfViewer(uuid))
                    },
                    onOpenDocumentActions = { uuid -> navigator.goTo(DocumentActions(uuid)) },
                    onOpenFolder = { uuid -> navigator.goTo(FolderContents(uuid)) },
                    onOpenFolderActions = { uuid -> navigator.goTo(FolderActions(uuid)) },
                    onCreateFolder = { navigator.goTo(FolderEditor(parentUuid = key.folderUuid)) },
                    onFolderGone = { navigator.removeDestination(key) },
                ),
            selectedDocumentId = selectedDocumentId,
        )
    }

    entry<FolderEditor>(metadata = OverlaySceneStrategy.overlay()) { key ->
        val viewModel = folderViewModel(key, key.folderUuid, key.parentUuid, navigator)
        val state by viewModel.state.collectAsStateWithLifecycle()
        // Nothing to fill the form with until the folder is read.
        if (state.isLoading) return@entry
        val folder = state.folder

        // What has been typed and picked, not what the folder is: held here so that it survives
        // process death, and the sheet becoming a dialog when the window changes shape.
        var name by rememberSaveable { mutableStateOf(folder?.name.orEmpty()) }
        var colorKey by rememberSaveable { mutableStateOf(folder?.color) }
        var iconKey by rememberSaveable { mutableStateOf(folder?.icon) }

        FolderEditorForm(
            isNew = state.isNew,
            name = name,
            color = LabelColor.of(colorKey),
            icon = FolderIcon.of(iconKey),
            nameError = state.nameError,
            onNameChange = {
                name = it
                viewModel.onSendIntent(FolderIntent.NameEdited)
            },
            onColorChange = { colorKey = it?.key },
            onIconChange = { iconKey = it.key },
            onDismiss = navigator::goBack,
            onConfirm = {
                viewModel.onSendIntent(
                    FolderIntent.Save(name, LabelColor.of(colorKey), FolderIcon.of(iconKey))
                )
            },
        )
    }

    entry<FolderActions>(metadata = OverlaySceneStrategy.overlay(OverlayPreference.AlwaysSheet)) {
        key ->
        val viewModel = folderViewModel(key, key.folderUuid, parentUuid = null, navigator)
        val state by viewModel.state.collectAsStateWithLifecycle()
        val folder = state.folder ?: return@entry

        FolderActionsContent(
            folder = folder,
            onEdit = { navigator.goTo(FolderEditor(folderUuid = folder.uuid)) },
            onSetPinned = { viewModel.onSendIntent(FolderIntent.SetPinned(it)) },
            onMove = { navigator.goTo(MoveToFolder(folderUuid = folder.uuid)) },
            onDelete = { navigator.goTo(DeleteFolder(folder.uuid)) },
            stacked = LocalOverlayContext.current.hasRoomToStack,
        )
    }

    entry<DeleteFolder>(metadata = OverlaySceneStrategy.overlay()) { key ->
        val viewModel = folderViewModel(key, key.folderUuid, parentUuid = null, navigator)
        val state by viewModel.state.collectAsStateWithLifecycle()
        val folder = state.folder ?: return@entry

        DeleteFolderForm(
            folder = folder,
            onDismiss = navigator::goBack,
            onConfirm = { viewModel.onSendIntent(FolderIntent.ConfirmDelete) },
        )
    }

    entry<MoveToFolder>(metadata = OverlaySceneStrategy.overlay()) { key ->
        val viewModel: MoveToFolderViewModel = koinViewModel { parametersOf(key) }
        val state by viewModel.state.collectAsStateWithLifecycle()
        val currentNavigator by rememberUpdatedState(navigator)

        ShowMessages(viewModel.defaultEvents)
        LaunchedEffect(viewModel) {
            viewModel.effects.collectLatest { effect ->
                when (effect) {
                    // The thing is where it was asked to go, so nothing more is to be done to it
                    // from here: the picker goes, and the actions it was opened from with it.
                    MoveToFolderEffect.Close ->
                        currentNavigator.goBackWhile {
                            it == key || it is FolderActions || it is DocumentActions
                        }
                }
            }
        }

        MoveToFolderForm(
            state = state,
            onBrowse = { viewModel.onSendIntent(MoveToFolderIntent.Browse(it)) },
            onCreateFolder = {
                navigator.goTo(FolderEditor(parentUuid = state.location?.uuid))
            },
            onDismiss = navigator::goBack,
            onConfirm = { viewModel.onSendIntent(MoveToFolderIntent.MoveHere) },
        )
    }
}

/**
 * One ViewModel per navigation entry: each overlay follows its own folder, and is cleared with it.
 */
@Composable
private fun folderViewModel(
    entry: NavKey,
    folderUuid: String?,
    parentUuid: String?,
    navigator: Navigator,
): FolderViewModel {
    val viewModel: FolderViewModel = koinViewModel {
        parametersOf(FolderRef(folderUuid), FolderRef(parentUuid))
    }
    val currentNavigator by rememberUpdatedState(navigator)

    ShowMessages(viewModel.defaultEvents)
    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                // This overlay, not whatever is on top of the stack.
                FolderEffect.Close -> currentNavigator.removeDestination(entry)

                // The folder is gone, so every overlay standing on it goes with it. Its own
                // screen, if it is open underneath, closes itself the same way.
                FolderEffect.CloseAll -> currentNavigator.goBackWhile { it.isFolderOverlay }
            }
        }
    }

    return viewModel
}

private val NavKey.isFolderOverlay: Boolean
    get() =
        this is FolderActions ||
            this is FolderEditor ||
            this is DeleteFolder ||
            this is MoveToFolder
