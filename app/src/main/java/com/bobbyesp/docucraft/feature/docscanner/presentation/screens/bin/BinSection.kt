/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.bin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.docucraft.core.presentation.navigation.Navigator
import com.bobbyesp.docucraft.core.presentation.navigation.overlay.LocalOverlayContext
import com.bobbyesp.docucraft.core.presentation.navigation.overlay.OverlayPreference
import com.bobbyesp.docucraft.core.presentation.navigation.overlay.OverlaySceneStrategy
import com.bobbyesp.docucraft.feature.docscanner.di.BinDocumentRef
import com.bobbyesp.docucraft.feature.docscanner.navigation.Bin
import com.bobbyesp.docucraft.feature.docscanner.navigation.BinDocumentActions
import com.bobbyesp.docucraft.feature.docscanner.navigation.DeleteForever
import com.bobbyesp.docucraft.feature.docscanner.navigation.EmptyBin
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.organization.ShowMessages
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The bin, and what is done in it. The list is a screen; a document's actions and the two
 * confirmations are overlays, placed by [OverlaySceneStrategy].
 */
fun EntryProviderScope<NavKey>.binSection(navigator: Navigator) {
    entry<Bin> { key ->
        val viewModel = binViewModel(key, documentUuid = null, navigator)
        val state by viewModel.state.collectAsStateWithLifecycle()

        BinScreen(
            uiState = state,
            onBack = navigator::goBack,
            onOpenDocumentActions = { uuid -> navigator.goTo(BinDocumentActions(uuid)) },
            onEmptyBin = { navigator.goTo(EmptyBin) },
        )
    }

    entry<BinDocumentActions>(
        metadata = OverlaySceneStrategy.overlay(OverlayPreference.AlwaysSheet)
    ) { key ->
        val viewModel = binViewModel(key, key.documentUuid, navigator)
        val state by viewModel.state.collectAsStateWithLifecycle()
        val binned = state.document ?: return@entry

        BinDocumentActionsContent(
            binned = binned,
            onRestore = { viewModel.onSendIntent(BinIntent.Restore) },
            onDeleteForever = { navigator.goTo(DeleteForever(key.documentUuid)) },
            stacked = LocalOverlayContext.current.hasRoomToStack,
        )
    }

    entry<DeleteForever>(metadata = OverlaySceneStrategy.overlay()) { key ->
        val viewModel = binViewModel(key, key.documentUuid, navigator)
        val state by viewModel.state.collectAsStateWithLifecycle()
        val binned = state.document ?: return@entry

        DeleteForeverForm(
            binned = binned,
            onDismiss = navigator::goBack,
            onConfirm = { viewModel.onSendIntent(BinIntent.ConfirmDeleteForever) },
        )
    }

    entry<EmptyBin>(metadata = OverlaySceneStrategy.overlay()) { key ->
        val viewModel = binViewModel(key, documentUuid = null, navigator)
        val state by viewModel.state.collectAsStateWithLifecycle()

        EmptyBinForm(
            count = state.documents.size,
            onDismiss = navigator::goBack,
            onConfirm = { viewModel.onSendIntent(BinIntent.ConfirmEmpty) },
        )
    }
}

@Composable
private fun binViewModel(entry: NavKey, documentUuid: String?, navigator: Navigator): BinViewModel {
    val viewModel: BinViewModel = koinViewModel { parametersOf(BinDocumentRef(documentUuid)) }
    val currentNavigator by rememberUpdatedState(navigator)

    ShowMessages(viewModel.defaultEvents)
    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                BinEffect.Close -> currentNavigator.removeDestination(entry)

                // The document left the bin, restored or deleted: the confirmation goes, and the
                // sheet it was opened from.
                BinEffect.CloseAll ->
                    currentNavigator.goBackWhile { it is BinDocumentActions || it is DeleteForever }
            }
        }
    }

    return viewModel
}
