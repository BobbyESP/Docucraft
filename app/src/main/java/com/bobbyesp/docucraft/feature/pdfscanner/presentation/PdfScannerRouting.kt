package com.bobbyesp.docucraft.feature.pdfscanner.presentation

import android.util.Log
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.navigation
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.common.LocalSonner
import com.bobbyesp.docucraft.core.presentation.common.Route
import com.bobbyesp.docucraft.core.presentation.motion.animatedComposable
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.pages.home.HomePage
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.pages.home.HomeViewModel
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.pages.home.HomeViewModel.UiEvent.ScanResult
import com.dokar.sonner.ToastType
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

fun NavGraphBuilder.pdfScannerRouting() {
    navigation<Route.DocucraftNavigator>(
        startDestination = Route.DocucraftNavigator.Home
    ) {
        animatedComposable<Route.DocucraftNavigator.Home> {
            val sonner = LocalSonner.current
            val context = LocalContext.current
            val vm = koinViewModel<HomeViewModel>()
            val scannedPdfsState = vm.scannedPdfsListFlow.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                vm.eventFlow.collectLatest { event ->
                    when (event) {
                        is ScanResult -> {
                            val (message, type) = if (event is ScanResult.Success) {
                                context.getString(R.string.pdf_saved_successfully) to ToastType.Success
                            } else {
                                context.getString(R.string.pdf_saved_error) to ToastType.Error
                            }
                            sonner.show(message = message, type = type)
                        }

                        is HomeViewModel.UiEvent.IssueOpening -> {
                            val errorMessage = when (event) {
                                is HomeViewModel.UiEvent.IssueOpening.PdfViewer ->
                                    context.getString(R.string.issue_opening_pdf_viewer)
                                is HomeViewModel.UiEvent.IssueOpening.ShareIntent ->
                                    context.getString(R.string.issue_sharing_pdf)
                            }
                            sonner.show(message = errorMessage, type = ToastType.Error)
                        }

                        is HomeViewModel.UiEvent.SavingResult -> {
                            val (message, type) = when (event) {
                                is HomeViewModel.UiEvent.SavingResult.Success -> {
                                    context.getString(
                                        R.string.pdf_saved_successfully_to,
                                        event.uri.path ?: ""
                                    ) to ToastType.Success
                                }
                                is HomeViewModel.UiEvent.SavingResult.Failure -> {
                                    val errorMsg = event.error.message ?:
                                        context.getString(R.string.unknown_error)
                                    context.getString(
                                        R.string.pdf_saved_error_with_reason,
                                        errorMsg
                                    ) to ToastType.Error
                                }
                            }
                            sonner.show(message = message, type = type)
                        }

                        is HomeViewModel.UiEvent.DeleteResult -> {
                            val (message, type) = if (event is HomeViewModel.UiEvent.DeleteResult.Success) {
                                context.getString(R.string.pdf_deleted_successfully) to ToastType.Success
                            } else {
                                context.getString(R.string.pdf_deleted_error) to ToastType.Error
                            }
                            sonner.show(message = message, type = type)
                        }
                    }
                }
            }

            HomePage(
                scannedPdfsState = scannedPdfsState,
                onEvent = vm::onEvent,
            )
        }
    }
}