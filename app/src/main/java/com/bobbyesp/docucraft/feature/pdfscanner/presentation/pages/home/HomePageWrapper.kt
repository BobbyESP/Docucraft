package com.bobbyesp.docucraft.feature.pdfscanner.presentation.pages.home

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.common.LocalSonner
import com.bobbyesp.docucraft.core.util.state.TemporalState
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.pages.home.dialogs.EditPdfDetailsDialog
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.pages.home.dialogs.DeletePdfConfirmationDialog
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.pages.home.viewmodel.HomeViewModel
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.pages.home.viewmodel.HomeViewModel.UiEvent.ScanResult
import com.dokar.sonner.ToastType
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePageWrapper() {
    val sonner = LocalSonner.current
    val context = LocalContext.current
    val vm = koinViewModel<HomeViewModel>()

    val uiState = vm.uiState.collectAsStateWithLifecycle().value

    LaunchedEffect(Unit) {
        vm.eventFlow.collectLatest { event ->
            when (event) {
                is ScanResult -> {
                    val (message, type) =
                        when (event) {
                            is ScanResult.Success -> {
                                context.getString(R.string.pdf_saved_successfully) to
                                    ToastType.Success
                            }

                            is ScanResult.Failure -> {
                                val errorMsg =
                                    event.error.message ?: context.getString(R.string.unknown_error)
                                context.getString(R.string.pdf_saved_error_with_reason, errorMsg) to
                                    ToastType.Error
                            }

                            ScanResult.Cancelled -> {
                                context.getString(R.string.scan_cancelled) to ToastType.Info
                            }
                        }
                    sonner.show(message = message, type = type)
                }

                is HomeViewModel.UiEvent.IssueOpening -> {
                    val errorMessage =
                        when (event) {
                            is HomeViewModel.UiEvent.IssueOpening.PdfViewer ->
                                context.getString(R.string.issue_opening_pdf_viewer)

                            is HomeViewModel.UiEvent.IssueOpening.ShareIntent ->
                                context.getString(R.string.issue_sharing_pdf)
                        }
                    sonner.show(message = errorMessage, type = ToastType.Error)
                }

                is HomeViewModel.UiEvent.SavingResult -> {
                    val (message, type) =
                        when (event) {
                            is HomeViewModel.UiEvent.SavingResult.Success -> {
                                context.getString(
                                    R.string.pdf_saved_successfully_to,
                                    event.uri.path ?: "",
                                ) to ToastType.Success
                            }

                            is HomeViewModel.UiEvent.SavingResult.Failure -> {
                                val errorMsg =
                                    event.error.message ?: context.getString(R.string.unknown_error)
                                context.getString(R.string.pdf_saved_error_with_reason, errorMsg) to
                                    ToastType.Error
                            }

                            HomeViewModel.UiEvent.SavingResult.Cancelled -> {
                                context.getString(R.string.pdf_saving_cancelled) to ToastType.Info
                            }
                        }
                    sonner.show(message = message, type = type)
                }

                is HomeViewModel.UiEvent.DeleteResult -> {
                    val (message, type) =
                        if (event is HomeViewModel.UiEvent.DeleteResult.Success) {
                            context.getString(R.string.pdf_deleted_successfully) to
                                ToastType.Success
                        } else {
                            context.getString(R.string.pdf_deleted_error) to ToastType.Error
                        }
                    sonner.show(message = message, type = type)
                }

            //                is HomeViewModel.UiEvent.PdfInformation -> {
            //                    when(event) {
            //                        is HomeViewModel.UiEvent.PdfInformation.Show -> {
            //
            //                        }
            //
            //                        HomeViewModel.UiEvent.PdfInformation.Dismiss -> {
            //
            // vm.onEvent(HomeViewModel.Event.NotifyUserAction.DismissPdfInformation)
            //                        }
            //                    }
            //                }
            }
        }
    }

    if (uiState.pdfToBeRemoved is TemporalState.Present) {
        val scannedPdf = uiState.pdfToBeRemoved.value
        DeletePdfConfirmationDialog(
            modifier = Modifier,
            scannedPdf = scannedPdf,
            onDismiss = { vm.onEvent(HomeViewModel.Event.PdfAction.Delete(null)) },
            onConfirm = { vm.onEvent(HomeViewModel.Event.PdfAction.Delete(scannedPdf.id)) },
        )
    }

    if (uiState.pdfToBeModified is TemporalState.Present) {
        val scannedPdf = uiState.pdfToBeModified.value
        EditPdfDetailsDialog(
            modifier = Modifier,
            onDismiss = {
                vm.onEvent(HomeViewModel.Event.NotifyUserAction.DismissPdfTitleDescriptionDialog)
            },
            onConfirm = { title, description ->
                vm.onEvent(
                    HomeViewModel.Event.PdfAction.ModifyTitleDescription(
                        pdfId = scannedPdf.id,
                        title = title,
                        description = description,
                    )
                )
            },
            title = scannedPdf.title,
            description = scannedPdf.description,
        )
    }

    if (uiState.pdfToShowInformation is TemporalState.Present) {
        val scannedPdf = uiState.pdfToShowInformation.value

        // TODO

    }

    HomePage(
        scannedPdfs = uiState.scannedPdfs,
        loadingState = uiState.loadingState,
        filteredPdfs = uiState.filteredPdfs,
        filterOptions = uiState.filterOptions,
        searchState = uiState.searchState,
        onEvent = vm::onEvent,
    )
}
