/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.docucraft.core.domain.notifications.InAppNotification
import com.bobbyesp.docucraft.core.domain.repository.logScreenView
import com.bobbyesp.docucraft.core.presentation.common.LocalAnalyticsHelper
import com.bobbyesp.docucraft.core.presentation.common.LocalNotificationsService
import com.bobbyesp.docucraft.core.util.events.UiEvent
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.viewmodel.HomeViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Suppress("EffectKeys")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenDocument: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDocumentActions: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
    selectedDocumentId: String? = null,
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val analyticsHelper = LocalAnalyticsHelper.current

    HandleHomeMessages(uiEventFlow = viewModel.defaultEvents)

    HomeContent(
        modifier = modifier,
        uiState = uiState,
        onAction = viewModel::onSendIntent,
        // Straight from the tap. Going somewhere is not work for the state holder to do, and
        // routing it through one only opened a gap between the asking and the going.
        onOpenDocument = { uuid ->
            onOpenDocument(uuid)
            analyticsHelper.logScreenView("PdfViewer")
        },
        onOpenSettings = {
            onOpenSettings()
            analyticsHelper.logScreenView("Settings")
        },
        onOpenDocumentActions = onOpenDocumentActions,
        selectedDocumentId = selectedDocumentId,
    )
}

/**
 * Messages do wait for somebody to read them, unlike anything the screen is asked to do: a scan
 * that finished while the catalogue was off screen still has to say how it went.
 */
@Composable
private fun HandleHomeMessages(uiEventFlow: Flow<UiEvent>) {
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
