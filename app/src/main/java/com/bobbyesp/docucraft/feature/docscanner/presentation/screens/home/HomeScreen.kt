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
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeUiAction
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeUiEffect
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.sheet.DocumentDialogWrapper
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
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HandleHomeUiEffects(
        uiEffectFlow = viewModel.uiEffect,
        uiEventFlow = viewModel.events,
        onNavigate = onNavigate,
    )

    uiState.sheetState?.let { sheetState ->
        DocumentDialogWrapper(
            sheetState = sheetState,
            onAction = viewModel::onSheetAction,
        )
    }

    HomeContent(
        modifier = modifier,
        uiState = uiState,
        onAction = viewModel::onAction,
        onOpenSheet = { id -> viewModel.onAction(HomeUiAction.OpenSheet(id)) },
    )
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
