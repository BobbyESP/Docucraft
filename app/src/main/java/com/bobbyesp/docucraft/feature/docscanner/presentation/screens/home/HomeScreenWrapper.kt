package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home

import android.annotation.SuppressLint
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.docucraft.core.presentation.common.Route
import com.bobbyesp.docucraft.core.util.state.TemporalState
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeUiAction
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeUiEffect
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.dialogs.DeleteDocumentConfirmationDialog
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.dialogs.EditDocumentDetailsDialog
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.viewmodel.HomeViewModel
import com.bobbyesp.docucraft.feature.docscanner.presentation.util.DocumentScannerLauncher
import kotlinx.coroutines.flow.Flow
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenWrapper(
    onNavigate: (Route) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val onScanClick: () -> Unit = { viewModel.onAction(HomeUiAction.OnScanButtonClicked) }

    HandleHomeUiEffects(
        effectFlow = viewModel.uiEffect,
        onScanResult = { result -> viewModel.onAction(HomeUiAction.OnScanResultReceived(result)) },
        onNavigate = onNavigate
    )

    val pdfToBeRemoved = uiState.documentForRemoval
    if (pdfToBeRemoved is TemporalState.Present<*>) {
        val scannedDocument = pdfToBeRemoved.value as ScannedDocument
        DeleteDocumentConfirmationDialog(
            scannedDocument = scannedDocument,
            onDismiss = { viewModel.onAction(HomeUiAction.DeleteDocument(null)) },
            onConfirm = { viewModel.onAction(HomeUiAction.DeleteDocument(scannedDocument.id)) },
            modifier = Modifier,
        )
    }

    val pdfToBeModified = uiState.documentForModification
    if (pdfToBeModified is TemporalState.Present<*>) {
        val scannedDocument = pdfToBeModified.value as ScannedDocument
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

    HomeScreen(
        modifier = modifier,
        uiState = uiState,
        onAction = viewModel::onAction,
        onScanClick = onScanClick,
    )
}

@Suppress("EffectKeys")
@SuppressLint("LocalContextResourcesRead")
@Composable
private fun HandleHomeUiEffects(
    effectFlow: Flow<HomeUiEffect>,
    onScanResult: (ActivityResult) -> Unit,
    onNavigate: (Route) -> Unit
) {
    val activity = LocalActivity.current
    val currentOnNavigate by rememberUpdatedState(onNavigate)

    val scannerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            onScanResult(result)
        }

    LaunchedEffect(Unit) {
        effectFlow.collect { effect ->
            when (effect) {
                HomeUiEffect.LaunchScanner -> {
                    activity?.let { act ->
                        DocumentScannerLauncher.launch(act, scannerLauncher)
                    }
                }

                is HomeUiEffect.Navigate -> {
                    currentOnNavigate(effect.route)
                }
            }
        }
    }
}
