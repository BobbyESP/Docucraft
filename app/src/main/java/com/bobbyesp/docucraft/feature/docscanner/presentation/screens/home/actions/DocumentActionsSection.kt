/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.actions

import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.docucraft.core.domain.notifications.InAppNotification
import com.bobbyesp.docucraft.core.presentation.common.LocalNotificationsService
import com.bobbyesp.docucraft.core.presentation.navigation.Navigator
import com.bobbyesp.docucraft.core.presentation.navigation.overlay.LocalOverlayContext
import com.bobbyesp.docucraft.core.presentation.navigation.overlay.OverlayPreference
import com.bobbyesp.docucraft.core.presentation.navigation.overlay.OverlayPresentation
import com.bobbyesp.docucraft.core.presentation.navigation.overlay.OverlaySceneStrategy
import com.bobbyesp.docucraft.core.util.events.UiEvent
import com.bobbyesp.docucraft.feature.docscanner.navigation.DeleteDocument
import com.bobbyesp.docucraft.feature.docscanner.navigation.DocumentActions
import com.bobbyesp.docucraft.feature.docscanner.navigation.EditDocument
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.sheet.DocumentActionsContent
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.dialogs.DeleteDocumentDialog
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.dialogs.DeleteDocumentSheet
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.dialogs.EditDocumentDetailsDialog
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.dialogs.EditDocumentDetailsSheet
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Acting on one document: the grid of actions, and editing or deleting from it.
 *
 * All three are overlays over whatever the catalogue is showing, so they are declared as ordinary
 * destinations and left to [OverlaySceneStrategy] to place. Nothing here knows how wide the window
 * is; the strategy decides between a sheet and a dialog and says which it chose, and the only
 * difference that makes is which body a destination renders.
 *
 * The actions grid asks for a sheet whatever the window, which is what it was before: a row of
 * choices reads as a sheet on any size, and turning it into a dialog would only shrink it.
 */
fun EntryProviderScope<NavKey>.documentActionsSection(navigator: Navigator) {
    entry<DocumentActions>(
        metadata = OverlaySceneStrategy.overlay(OverlayPreference.AlwaysSheet)
    ) { key ->
        val viewModel = documentActionsViewModel(key, key.documentUuid, navigator)
        val state by viewModel.state.collectAsStateWithLifecycle()
        val document = state.document ?: return@entry

        DocumentActionsContent(
            scannedDocument = document,
            onSave = { viewModel.onSendIntent(DocumentActionsIntent.Export) },
            onShare = { viewModel.onSendIntent(DocumentActionsIntent.Share) },
            onDelete = { navigator.goTo(DeleteDocument(key.documentUuid)) },
            onModifyFields = { navigator.goTo(EditDocument(key.documentUuid)) },
            stacked = LocalOverlayContext.current.hasRoomToStack,
        )
    }

    entry<EditDocument>(metadata = OverlaySceneStrategy.overlay()) { key ->
        val viewModel = documentActionsViewModel(key, key.documentUuid, navigator)
        val state by viewModel.state.collectAsStateWithLifecycle()
        val document = state.document ?: return@entry

        // Held here rather than in the ViewModel because it is what has been typed, not what the
        // document is; `rememberSaveable` carries it through process death and through the sheet
        // becoming a dialog when the window changes shape mid-edit.
        var title by rememberSaveable(key.documentUuid) { mutableStateOf(document.title.orEmpty()) }
        var description by
            rememberSaveable(key.documentUuid) { mutableStateOf(document.description.orEmpty()) }

        val form = EditDocumentUiState.of(title, description)
        val confirm = {
            viewModel.onSendIntent(DocumentActionsIntent.ConfirmEdit(title, description))
        }

        when (LocalOverlayContext.current.presentation) {
            OverlayPresentation.Sheet ->
                EditDocumentDetailsSheet(
                    state = form,
                    onTitleChange = { title = it },
                    onDescriptionChange = { description = it },
                    onPopDialog = navigator::goBack,
                    onConfirmEdit = confirm,
                )

            OverlayPresentation.Dialog ->
                EditDocumentDetailsDialog(
                    state = form,
                    onTitleChange = { title = it },
                    onDescriptionChange = { description = it },
                    onDismiss = navigator::goBack,
                    onConfirmEdit = confirm,
                    modifier = Modifier.widthIn(max = DialogMaxWidth),
                )
        }
    }

    entry<DeleteDocument>(metadata = OverlaySceneStrategy.overlay()) { key ->
        val viewModel = documentActionsViewModel(key, key.documentUuid, navigator)
        val state by viewModel.state.collectAsStateWithLifecycle()
        val document = state.document ?: return@entry

        val confirm = { viewModel.onSendIntent(DocumentActionsIntent.ConfirmDelete) }

        when (LocalOverlayContext.current.presentation) {
            OverlayPresentation.Sheet ->
                DeleteDocumentSheet(
                    document = document,
                    onDismiss = navigator::goBack,
                    onConfirm = confirm,
                )

            OverlayPresentation.Dialog ->
                DeleteDocumentDialog(
                    scannedDocument = document,
                    onDismiss = navigator::goBack,
                    onConfirm = confirm,
                    modifier = Modifier.widthIn(max = DialogMaxWidth),
                )
        }
    }
}

/**
 * One ViewModel per navigation entry, courtesy of the entry-scoped `ViewModelStoreOwner`: each
 * overlay follows its own document and is cleared when it is popped.
 */
@Composable
private fun documentActionsViewModel(
    entry: NavKey,
    documentUuid: String,
    navigator: Navigator,
): DocumentActionsViewModel {
    val viewModel: DocumentActionsViewModel =
        koinViewModel(key = documentUuid) { parametersOf(documentUuid) }

    HandleDocumentActionsEffects(viewModel, navigator, entry)

    return viewModel
}

@Composable
private fun HandleDocumentActionsEffects(
    viewModel: DocumentActionsViewModel,
    navigator: Navigator,
    entry: NavKey,
) {
    val currentNavigator by rememberUpdatedState(navigator)
    val notifications = LocalNotificationsService.current

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                // This overlay, not whatever is on top of the stack. They are the same thing
                // right up until they are not, and `goBack` cannot tell the difference.
                DocumentActionsEffect.Close -> currentNavigator.removeDestination(entry)

                // The document is gone, so every overlay standing on it goes with it — the delete
                // confirmation and, underneath it, the actions grid.
                DocumentActionsEffect.CloseAll ->
                    currentNavigator.goBackWhile { it.isDocumentOverlay }
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.defaultEvents.collectLatest { event ->
            when (event) {
                is UiEvent.ShowMessage ->
                    notifications.show(
                        InAppNotification(message = event.message, type = event.type)
                    )
            }
        }
    }
}

private val NavKey.isDocumentOverlay: Boolean
    get() = this is DocumentActions || this is EditDocument || this is DeleteDocument

private val DialogMaxWidth = 560.dp
