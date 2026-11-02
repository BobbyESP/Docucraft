/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.bobbyesp.docucraft.feature.pdfviewer.domain.links.ResolveLinkUseCase
import com.bobbyesp.docucraft.feature.pdfviewer.domain.model.ViewerDocumentRef
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.PdfViewerViewModel
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.contract.PdfViewerIntent
import com.bobbyesp.docucraft.library.domain.model.BasicDocument
import org.koin.compose.koinInject

@Composable
fun PdfViewerScreen(
    viewModel: PdfViewerViewModel,
    document: ViewerDocumentRef,
    documentInfo: BasicDocument,
    onBack: () -> Unit,
    onOpenDetails: () -> Unit,
    onGoToPage: (currentPage: Int, pageCount: Int) -> Unit,
    onConfirmSaveCopy: (documentUuid: String) -> Unit,
    onOpenInLibrary: (documentUuid: String) -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
    initialSearchQuery: String? = null,
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = viewModel::onSendIntent
    val resolveLink: ResolveLinkUseCase = koinInject()
    val screenState =
        rememberPdfViewerScreenState(
            documentUri = documentInfo.uri,
            start = uiState.start?.position,
        )
    val pdf = screenState.pdf
    val search = screenState.search

    ReportReadingPosition(pdf, screenState.lastSeen) {
        onIntent(PdfViewerIntent.ReadingPositionChanged(it))
    }
    ConsumePageRequests(document, pdf)
    ReadVisiblePages(pdf, onIntent)
    DismissLinkPreviewOnMove(pdf, previewShown = uiState.linkPreview != null, onIntent)
    ReportDocumentLoad(pdf, screenState.loadError, onIntent)
    // After ReportDocumentLoad, which tells the ViewModel how many pages there are to search.
    ReportSearchQuery(search, pdf, onIntent)
    HighlightArrivalQuery(pdf, search, initialSearchQuery, onIntent)
    DismissKeyboardOnTouch(screenState)
    HandlePdfViewerEffects(
        effects = viewModel.effects,
        screenState = screenState,
        onConfirmSaveCopy = onConfirmSaveCopy,
        onOpenInLibrary = onOpenInLibrary,
    )
    HandleViewerMessages(viewModel.defaultEvents)

    // A failed document cannot be tapped to bring the bars back, and they hold the way out.
    LaunchedEffect(pdf.error) { if (pdf.error != null) screenState.chrome.show() }

    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = uiState.linkPreview != null || uiState.selection != null || search.isOpen,
        onBackCompleted = {
            when {
                uiState.linkPreview != null -> onIntent(PdfViewerIntent.DismissLinkPreview)
                uiState.selection != null -> onIntent(PdfViewerIntent.ClearSelection)
                else -> screenState.closeSearch()
            }
        },
    )

    PdfViewerContent(
        uiState = uiState,
        documentInfo = documentInfo,
        screenState = screenState,
        resolveLink = { link -> resolveLink(link, pdf.pageCount) },
        showBackButton = showBackButton,
        onBack = onBack,
        onOpenDetails = onOpenDetails,
        onGoToPage = onGoToPage,
        onIntent = onIntent,
        modifier = modifier,
    )
}
