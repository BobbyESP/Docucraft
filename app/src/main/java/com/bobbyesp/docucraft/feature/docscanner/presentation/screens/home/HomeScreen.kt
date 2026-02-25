package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home

import android.annotation.SuppressLint
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.docucraft.core.domain.notifications.InAppNotification
import com.bobbyesp.docucraft.core.domain.repository.InAppNotificationsService
import com.bobbyesp.docucraft.core.presentation.common.Route
import com.bobbyesp.docucraft.core.util.events.UiEvent
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.sheet.DocumentActionsSheet
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeDialog
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeUiAction
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeUiEffect
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.dialogs.DeleteDocumentConfirmationDialog
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.dialogs.EditDocumentDetailsDialog
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.viewmodel.HomeViewModel
import kotlinx.coroutines.flow.Flow
import org.koin.androidx.compose.koinViewModel

@Suppress("EffectKeys")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigate: (Route) -> Unit,
    notificationService: InAppNotificationsService,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.ShowMessage -> notificationService.show(
                    InAppNotification(
                        message = event.message,
                        type = event.type
                    )
                )
            }
        }
    }

    HandleHomeUiEffects(
        effectFlow = viewModel.uiEffect,
        onNavigate = onNavigate
    )

    when (uiState.dialogs.active) {
        is HomeDialog.Delete -> {
            val scannedDocument = (uiState.dialogs.active as HomeDialog.Delete).doc
            DeleteDocumentConfirmationDialog(
                scannedDocument = scannedDocument,
                onDismiss = { viewModel.onAction(HomeUiAction.DeleteDocument(null)) },
                onConfirm = { viewModel.onAction(HomeUiAction.DeleteDocument(scannedDocument.id)) },
                modifier = Modifier,
            )
        }

        is HomeDialog.Edit -> {
            val scannedDocument = (uiState.dialogs.active as HomeDialog.Edit).doc
            EditDocumentDetailsDialog(
                onDismiss = { viewModel.onAction(HomeUiAction.DismissDialogs) },
                onConfirm = { title, description ->
                    viewModel.onAction(
                        HomeUiAction.UpdateDocumentFields(
                            id = scannedDocument.id,
                            title = title,
                            description = description,
                        )
                    )
                },
                title = scannedDocument.title,
                description = scannedDocument.description,
                modifier = Modifier,
            )
        }

        is HomeDialog.Actions -> {
            val scannedDocument = (uiState.dialogs.active as HomeDialog.Actions).doc

            DocumentActionsSheet(
                scannedDocument = scannedDocument,
                onDismissRequest = { viewModel.onAction(HomeUiAction.DismissActionsSheet) },
                onSavePdf = { viewModel.onAction(HomeUiAction.SaveDocument(scannedDocument)) },
                onSharePdf = { viewModel.onAction(HomeUiAction.ShareDocument(scannedDocument.path)) },
                onDeletePdf = {
                    viewModel.onAction(
                        HomeUiAction.ShowDeleteConfirmation(
                            scannedDocument.id
                        )
                    )
                },
                onModifyPdfFields = { viewModel.onAction(HomeUiAction.ShowEditDialog(scannedDocument.id)) },
            )
        }

        null -> {}
    }

    HomeContent(
        modifier = modifier,
        uiState = uiState,
        onAction = viewModel::onAction,
    )
}

@Suppress("EffectKeys")
@SuppressLint("LocalContextResourcesRead")
@Composable
private fun HandleHomeUiEffects(
    effectFlow: Flow<HomeUiEffect>,
    onNavigate: (Route) -> Unit
) {
    val currentOnNavigate by rememberUpdatedState(onNavigate)

    LaunchedEffect(Unit) {
        effectFlow.collect { effect ->
            when (effect) {
                is HomeUiEffect.Navigate -> {
                    currentOnNavigate(effect.route)
                }
            }
        }
    }
}
