package com.bobbyesp.docucraft.feature.pdfscanner.presentation.pages.home

import android.annotation.SuppressLint
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.domain.repository.DocumentScannerRepository
import com.bobbyesp.docucraft.core.presentation.common.LocalSonner
import com.bobbyesp.docucraft.core.util.state.TemporalState
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.pages.home.dialogs.DeletePdfConfirmationDialog
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.pages.home.dialogs.EditPdfDetailsDialog
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.pages.home.viewmodel.HomeUiAction
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.pages.home.viewmodel.HomeUiEffect
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.pages.home.viewmodel.HomeViewModel
import com.dokar.sonner.ToastType
import com.dokar.sonner.ToasterState
import kotlinx.coroutines.flow.Flow
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePageWrapper() {
    val sonner = LocalSonner.current
    val vm = koinViewModel<HomeViewModel>()
    val documentScannerRepository = koinInject<DocumentScannerRepository>()

    val uiState = vm.uiState.collectAsStateWithLifecycle().value

    val onScanClick: () -> Unit = { vm.onAction(HomeUiAction.OnScanButtonClicked) }

    HandleHomeUiEffects(
        effectFlow = vm.uiEffect,
        sonner = sonner,
        documentScannerRepository = documentScannerRepository,
        onScanResult = { result -> vm.onAction(HomeUiAction.OnScanResultReceived(result)) },
    )

    if (uiState.pdfToBeRemoved is TemporalState.Present) {
        val scannedPdf = uiState.pdfToBeRemoved.value
        DeletePdfConfirmationDialog(
            modifier = Modifier,
            scannedPdf = scannedPdf,
            onDismiss = { vm.onAction(HomeUiAction.DeletePdf(null)) },
            onConfirm = { vm.onAction(HomeUiAction.DeletePdf(scannedPdf.id)) },
        )
    }

    if (uiState.pdfToBeModified is TemporalState.Present) {
        val scannedPdf = uiState.pdfToBeModified.value
        EditPdfDetailsDialog(
            modifier = Modifier,
            onDismiss = { vm.onAction(HomeUiAction.DismissDialogs) },
            onConfirm = { title, description ->
                vm.onAction(
                    HomeUiAction.UpdatePdfMetadata(
                        id = scannedPdf.id,
                        title = title,
                        description = description,
                    )
                )
            },
            title = scannedPdf.title,
            description = scannedPdf.description,
        )
    }

    HomePage(uiState = uiState, onAction = vm::onAction, onScanClick = onScanClick)
}

@SuppressLint("LocalContextResourcesRead")
@Composable
fun HandleHomeUiEffects(
    effectFlow: Flow<HomeUiEffect>,
    sonner: ToasterState,
    documentScannerRepository: DocumentScannerRepository,
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
                        documentScannerRepository.launchScanner(act, scannerLauncher)
                    }
                }
                is HomeUiEffect.ScanSuccess -> {
                    sonner.show(
                        message = context.resources.getString(R.string.pdf_saved_successfully),
                        type = ToastType.Success,
                    )
                }
                is HomeUiEffect.ScanFailure -> {
                    val errorMsg =
                        effect.error.message ?: context.resources.getString(R.string.unknown_error)
                    sonner.show(
                        message =
                            context.resources.getString(
                                R.string.pdf_saved_error_with_reason,
                                errorMsg,
                            ),
                        type = ToastType.Error,
                    )
                }
                is HomeUiEffect.OpenPdfViewerFailure -> {
                    sonner.show(
                        message = context.resources.getString(R.string.issue_opening_pdf_viewer),
                        type = ToastType.Error,
                    )
                }
                is HomeUiEffect.SharePdfFailure -> {
                    sonner.show(
                        message = context.resources.getString(R.string.issue_sharing_pdf),
                        type = ToastType.Error,
                    )
                }
                is HomeUiEffect.SaveSuccess -> {
                    val path = effect.uri.path ?: ""
                    sonner.show(
                        message =
                            context.resources.getString(R.string.pdf_saved_successfully_to, path),
                        type = ToastType.Success,
                    )
                }
                is HomeUiEffect.SaveFailure -> {
                    val errorMsg =
                        effect.error.message ?: context.resources.getString(R.string.unknown_error)
                    sonner.show(
                        message =
                            context.resources.getString(
                                R.string.pdf_saved_error_with_reason,
                                errorMsg,
                            ),
                        type = ToastType.Error,
                    )
                }
                HomeUiEffect.SaveCancelled -> {
                    sonner.show(
                        message = context.resources.getString(R.string.pdf_saving_cancelled),
                        type = ToastType.Info,
                    )
                }
                HomeUiEffect.DeleteSuccess -> {
                    sonner.show(
                        message = context.resources.getString(R.string.pdf_deleted_successfully),
                        type = ToastType.Success,
                    )
                }
                is HomeUiEffect.DeleteFailure -> {
                    sonner.show(
                        message = context.resources.getString(R.string.pdf_deleted_error),
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
