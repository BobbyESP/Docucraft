/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.docucraft.core.domain.notifications.InAppNotification
import com.bobbyesp.docucraft.core.domain.repository.logScreenView
import com.bobbyesp.docucraft.core.presentation.common.LocalAnalyticsHelper
import com.bobbyesp.docucraft.core.presentation.common.LocalNotificationsService
import com.bobbyesp.docucraft.core.util.events.UiEvent
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeEffect
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.sheet.DocumentDialogWrapper
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.viewmodel.HomeViewModel
import com.bobbyesp.docucraft.feature.shared.domain.BasicDocument
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Suppress("EffectKeys")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenDocument: (BasicDocument) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
    selectedDocumentId: String? = null,
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()

    HandleHomeUiEffects(
        uiEffectFlow = viewModel.effects,
        uiEventFlow = viewModel.defaultEvents,
        onOpenDocument = onOpenDocument,
        onOpenSettings = onOpenSettings,
    )

    uiState.sheetState?.let { sheetState ->
        DocumentDialogWrapper(sheetState = sheetState, onHomeIntent = viewModel::onSendIntent)
    }

    HomeContent(
        modifier = modifier,
        uiState = uiState,
        onAction = viewModel::onSendIntent,
        selectedDocumentId = selectedDocumentId,
    )
}

@Composable
private fun HandleHomeUiEffects(
    uiEffectFlow: Flow<HomeEffect>,
    uiEventFlow: Flow<UiEvent>,
    onOpenDocument: (BasicDocument) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val currentOnOpenDocument by rememberUpdatedState(onOpenDocument)
    val currentOnOpenSettings by rememberUpdatedState(onOpenSettings)
    val analyticsHelper = LocalAnalyticsHelper.current

    LaunchedEffect(uiEffectFlow) {
        uiEffectFlow.collectLatest { effect ->
            when (effect) {
                is HomeEffect.OpenDocument -> {
                    currentOnOpenDocument(effect.document)
                    analyticsHelper.logScreenView("PdfViewer")
                }

                HomeEffect.OpenSettings -> {
                    currentOnOpenSettings()
                    analyticsHelper.logScreenView("Settings")
                }
            }
        }
    }

    val notificationsService = LocalNotificationsService.current

    LaunchedEffect(uiEventFlow) {
        uiEventFlow.collectLatest { event ->
            when (event) {
                is UiEvent.ShowMessage ->
                    notificationsService.show(
                        InAppNotification(message = event.message, type = event.type)
                    )
            }
        }
    }
}
