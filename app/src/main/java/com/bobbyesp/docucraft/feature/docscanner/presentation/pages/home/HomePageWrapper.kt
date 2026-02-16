package com.bobbyesp.docucraft.feature.docscanner.presentation.pages.home

import android.annotation.SuppressLint
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.mlkit.domain.repository.DocumentScannerService
import com.bobbyesp.docucraft.core.presentation.common.LocalSonner
import com.bobbyesp.docucraft.core.util.state.TemporalState
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedPdf
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeUiAction
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeUiEffect
import com.bobbyesp.docucraft.feature.docscanner.presentation.pages.home.dialogs.DeletePdfConfirmationDialog
import com.bobbyesp.docucraft.feature.docscanner.presentation.pages.home.dialogs.EditPdfDetailsDialog
import com.bobbyesp.docucraft.feature.docscanner.presentation.pages.home.viewmodel.HomeViewModel
import com.dokar.sonner.ToastType
import com.dokar.sonner.ToasterState
import kotlinx.coroutines.flow.Flow
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePageWrapper(modifier: Modifier = Modifier, viewModel: HomeViewModel = koinViewModel()) {
    val sonner = LocalSonner.current
    val documentScannerService = koinInject<DocumentScannerService>()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val onScanClick: () -> Unit = { viewModel.onAction(HomeUiAction.OnScanButtonClicked) }

    HandleHomeUiEffects(
        effectFlow = viewModel.uiEffect,
        sonner = sonner,
        documentScannerService = documentScannerService,
        onScanResult = { result -> viewModel.onAction(HomeUiAction.OnScanResultReceived(result)) },
    )

    val pdfToBeRemoved = uiState.pdfToBeRemoved
    if (pdfToBeRemoved is TemporalState.Present<*>) {
        val scannedPdf = pdfToBeRemoved.value as ScannedPdf
        DeletePdfConfirmationDialog(
            scannedPdf = scannedPdf,
            onDismiss = { viewModel.onAction(HomeUiAction.DeletePdf(null)) },
            onConfirm = { viewModel.onAction(HomeUiAction.DeletePdf(scannedPdf.id)) },
            modifier = Modifier,
        )
    }

    val pdfToBeModified = uiState.pdfToBeModified
    if (pdfToBeModified is TemporalState.Present<*>) {
        val scannedPdf = pdfToBeModified.value as ScannedPdf
        EditPdfDetailsDialog(
            onDismiss = { viewModel.onAction(HomeUiAction.DismissDialogs) },
            onConfirm = { title, description ->
                viewModel.onAction(
                    HomeUiAction.UpdatePdfMetadata(
                        id = scannedPdf.id,
                        title = title,
                        description = description,
                    )
                )
            },
            title = scannedPdf.title,
            description = scannedPdf.description,
            modifier = Modifier,
        )
    }

    HomePage(
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
    sonner: ToasterState,
    documentScannerService: DocumentScannerService,
    onScanResult: (ActivityResult) -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current

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
                        documentScannerService.launchScanner(act, scannerLauncher)
                    }
                }
                is HomeUiEffect.ScanSuccess -> {
                    sonner.show(
                        message = context.resources.getString(R.string.doc_saved_successfully),
                        type = ToastType.Success,
                    )
                }
                is HomeUiEffect.ScanFailure -> {
                    val errorMsg =
                        effect.error.message ?: context.resources.getString(R.string.unknown_error)
                    sonner.show(
                        message =
                            context.resources.getString(
                                R.string.doc_saved_error_with_reason,
                                errorMsg,
                            ),
                        type = ToastType.Error,
                    )
                }
                is HomeUiEffect.OpenPdfViewerFailure -> {
                    sonner.show(
                        message = context.resources.getString(R.string.issue_opening_doc_viewer),
                        type = ToastType.Error,
                    )
                }
                is HomeUiEffect.SharePdfFailure -> {
                    sonner.show(
                        message = context.resources.getString(R.string.issue_sharing_doc),
                        type = ToastType.Error,
                    )
                }
                is HomeUiEffect.SaveSuccess -> {
                    val path = effect.uri.path ?: ""
                    sonner.show(
                        message =
                            context.resources.getString(R.string.doc_saved_successfully_to, path),
                        type = ToastType.Success,
                    )
                }
                is HomeUiEffect.SaveFailure -> {
                    val errorMsg =
                        effect.error.message ?: context.resources.getString(R.string.unknown_error)
                    sonner.show(
                        message =
                            context.resources.getString(
                                R.string.doc_saved_error_with_reason,
                                errorMsg,
                            ),
                        type = ToastType.Error,
                    )
                }
                HomeUiEffect.DeleteSuccess -> {
                    sonner.show(
                        message = context.resources.getString(R.string.doc_deleted_successfully),
                        type = ToastType.Success,
                    )
                }
                is HomeUiEffect.DeleteFailure -> {
                    sonner.show(
                        message = context.resources.getString(R.string.doc_deleted_error),
                        type = ToastType.Error,
                    )
                }
                is HomeUiEffect.ShowError -> {
                    sonner.show(
                        message =
                            effect.error.message
                                ?: context.resources.getString(R.string.unknown_error),
                        type = ToastType.Error,
                    )
                }
                is HomeUiEffect.ShowMessage -> {
                    sonner.show(message = effect.message, type = ToastType.Normal)
                }
                is HomeUiEffect.ShowPdfInfoDialog -> {
                    // TODO: Handle showing info dialog
                }
            }
        }
    }
}
