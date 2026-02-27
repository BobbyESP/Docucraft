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
import com.bobbyesp.docucraft.core.presentation.common.Route
import com.bobbyesp.docucraft.core.util.events.UiEvent
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeUiAction
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeUiEffect
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.sheet.DocumentSheetAction
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.sheet.DocumentSheetEffect
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.sheet.DocumentSheetViewModel
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.viewmodel.HomeViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Suppress("EffectKeys")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigate: (Route) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
    sheetViewModel: DocumentSheetViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HandleHomeUiEffects(
        uiEffectFlow = viewModel.uiEffect,
        uiEventFlow = viewModel.events,
        onNavigate = onNavigate,
    )

    // Bridge DocumentSheetViewModel one-shot effects to HomeViewModel use-cases.
    HandleSheetEffects(
        sheetEffectFlow = sheetViewModel.effects,
        visibleDocuments = uiState.visibleDocuments,
        onHomeAction = viewModel::onAction,
    )

    // The sheet manages its own visibility internally.
    DocumentDialogWrapper(
        onHomeAction = viewModel::onAction,
        sheetViewModel = sheetViewModel,
    )

    HomeContent(
        modifier = modifier,
        uiState = uiState,
        onAction = viewModel::onAction,
        onOpenSheet = { id -> sheetViewModel.onAction(DocumentSheetAction.Open(id)) },
    )
}

@Composable
private fun HandleSheetEffects(
    sheetEffectFlow: Flow<DocumentSheetEffect>,
    visibleDocuments: List<ScannedDocument>,
    onHomeAction: (HomeUiAction) -> Unit,
) {
    LaunchedEffect(sheetEffectFlow) {
        sheetEffectFlow.collectLatest { effect ->
            when (effect) {
                is DocumentSheetEffect.RequestDelete ->
                    onHomeAction(HomeUiAction.DeleteDocument(effect.documentId))

                is DocumentSheetEffect.RequestShare -> {
                    val doc = visibleDocuments.firstOrNull { it.id == effect.documentId } ?: return@collectLatest
                    onHomeAction(HomeUiAction.ShareDocument(doc.path))
                }

                is DocumentSheetEffect.RequestSave -> {
                    val doc = visibleDocuments.firstOrNull { it.id == effect.documentId } ?: return@collectLatest
                    onHomeAction(HomeUiAction.SaveDocument(doc))
                }
            }
        }
    }
}

@Composable
private fun HandleHomeUiEffects(
    uiEffectFlow: Flow<HomeUiEffect>,
    uiEventFlow: Flow<UiEvent>,
    onNavigate: (Route) -> Unit,
) {
    val currentOnNavigate by rememberUpdatedState(onNavigate)
    val analyticsHelper = LocalAnalyticsHelper.current

    LaunchedEffect(uiEffectFlow) {
        uiEffectFlow.collectLatest { effect ->
            when (effect) {
                is HomeUiEffect.Navigate -> {
                    currentOnNavigate(effect.route)
                    analyticsHelper.logScreenView(effect.route::class.simpleName.toString())
                }
            }
        }
    }

    val notificationsService = LocalNotificationsService.current

    LaunchedEffect(uiEventFlow) {
        uiEventFlow.collectLatest { event ->
            when (event) {
                is UiEvent.ShowMessage -> notificationsService.show(
                    InAppNotification(
                        message = event.message,
                        type = event.type,
                    )
                )
            }
        }
    }
}
