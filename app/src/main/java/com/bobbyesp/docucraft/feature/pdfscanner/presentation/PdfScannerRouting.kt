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

            LaunchedEffect(true) {
                vm.eventFlow.collectLatest { event ->
                    when(event) {
                        is ScanResult -> {
                            if(event is ScanResult.Success) {
                                sonner.show(
                                    message = context.getString(R.string.pdf_saved_successfully),
                                    type = ToastType.Success
                                )
                            } else {
                                sonner.show(
                                    message = context.getString(R.string.pdf_saved_error),
                                    type = ToastType.Error
                                )
                            }
                        }

                        is HomeViewModel.UiEvent.IssueOpening -> {
                            val errorMessage: String = when (event) {
                                is HomeViewModel.UiEvent.IssueOpening.PdfViewer -> {
                                    context.getString(R.string.issue_opening_pdf_viewer)
                                }

                                is HomeViewModel.UiEvent.IssueOpening.ShareIntent -> {
                                    context.getString(R.string.issue_sharing_pdf)
                                }
                            }

                            sonner.show(
                                message = errorMessage,
                                type = ToastType.Error
                            )
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