/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.docucraft.core.presentation.navigation.Navigator
import com.bobbyesp.docucraft.core.presentation.navigation.pane.LocalPaneContext
import com.bobbyesp.docucraft.feature.pdfviewer.domain.model.ViewerDocumentRef
import com.bobbyesp.docucraft.feature.pdfviewer.navigation.ExternalPdfViewer
import com.bobbyesp.docucraft.feature.pdfviewer.navigation.GoToPage
import com.bobbyesp.docucraft.feature.pdfviewer.navigation.PdfDocumentDetails
import com.bobbyesp.docucraft.feature.pdfviewer.navigation.PdfViewer
import com.bobbyesp.docucraft.feature.pdfviewer.navigation.SaveCopyToLibrary
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.contract.ViewerDocumentState
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.details.pdfDocumentDetailsSection
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.library.saveCopyToLibrarySection
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.pages.goToPageSection
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.screens.PdfViewerScreen
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The PDF viewer is the detail pane of the list-detail layout: side by side with Home on expanded
 * windows, full screen on compact ones (where it shows its own back button). Which of the two it
 * got is read from the scene rather than measured off the window.
 *
 * The route names a document rather than carrying one, and the entry's [PdfViewerViewModel] follows
 * it in the catalogue: on expanded windows Home stays on screen next to the viewer, which means the
 * document can be renamed, or deleted, while it is open.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.pdfViewerSection(navigator: Navigator) {
    entry<PdfViewer>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
        // One per entry, courtesy of the entry-scoped `ViewModelStoreOwner`.
        val viewModel: PdfViewerViewModel =
            koinViewModel(key = route.documentUuid) {
                parametersOf(ViewerDocumentRef.Catalogued(route.documentUuid))
            }
        val state by viewModel.state.collectAsStateWithLifecycle()
        val document = state.document

        // Only ever when the catalogue has actually said the document is gone, and only ever this
        // entry. `goBack` here would pop whatever is on top, which need not be the viewer.
        LaunchedEffect(document, route) {
            if (document is ViewerDocumentState.Gone) navigator.removeDestination(route)
        }

        state.readyDocument?.let { ready ->
            val ref = ViewerDocumentRef.Catalogued(route.documentUuid)
            PdfViewerScreen(
                viewModel = viewModel,
                document = ref,
                documentInfo = ready,
                onBack = navigator::goBack,
                onOpenDetails = { navigator.goTo(PdfDocumentDetails(ref)) },
                onGoToPage = { current, count -> navigator.goTo(GoToPage(ref, current, count)) },
                onConfirmSaveCopy = { uuid -> navigator.goTo(SaveCopyToLibrary(uuid)) },
                onOpenInLibrary = { uuid -> navigator.goTo(PdfViewer(uuid)) },
                // Beside the list there is already a way back on screen; filling the window there
                // is not. The scene knows which of the two happened; this does not have to.
                showBackButton = LocalPaneContext.current.providesOwnBackAffordance,
            )
        }
    }

    pdfDocumentDetailsSection(navigator)
    goToPageSection(navigator)
    saveCopyToLibrarySection(navigator)
}

/**
 * The root of `PdfViewerActivity`'s own back stack: a document another app handed over. It leaves
 * through [onClose], since at the root there is nothing to go back to inside the app.
 */
fun EntryProviderScope<NavKey>.externalPdfViewerSection(
    navigator: Navigator,
    onClose: () -> Unit,
) {
    entry<ExternalPdfViewer> { key ->
        val ref = ViewerDocumentRef.External(uri = key.uri, displayName = key.displayName)
        val viewModel: PdfViewerViewModel = koinViewModel { parametersOf(ref) }
        val state by viewModel.state.collectAsStateWithLifecycle()

        state.readyDocument?.let { ready ->
            PdfViewerScreen(
                viewModel = viewModel,
                document = ref,
                documentInfo = ready,
                onBack = onClose,
                onOpenDetails = { navigator.goTo(PdfDocumentDetails(ref)) },
                onGoToPage = { current, count -> navigator.goTo(GoToPage(ref, current, count)) },
                onConfirmSaveCopy = { uuid -> navigator.goTo(SaveCopyToLibrary(uuid)) },
                onOpenInLibrary = { uuid -> navigator.goTo(PdfViewer(uuid)) },
                showBackButton = true,
            )
        }
    }

    // The document of the library with the same content as the one handed over, when the reader
    // asks for it: on top of that one, so that back returns to it. There is no list here to sit
    // beside, so it is a plain destination.
    entry<PdfViewer> { route ->
        val ref = ViewerDocumentRef.Catalogued(route.documentUuid)
        val viewModel: PdfViewerViewModel =
            koinViewModel(key = route.documentUuid) { parametersOf(ref) }
        val state by viewModel.state.collectAsStateWithLifecycle()
        val document = state.document

        LaunchedEffect(document, route) {
            if (document is ViewerDocumentState.Gone) navigator.removeDestination(route)
        }

        state.readyDocument?.let { ready ->
            PdfViewerScreen(
                viewModel = viewModel,
                document = ref,
                documentInfo = ready,
                onBack = navigator::goBack,
                onOpenDetails = { navigator.goTo(PdfDocumentDetails(ref)) },
                onGoToPage = { current, count -> navigator.goTo(GoToPage(ref, current, count)) },
                onConfirmSaveCopy = { uuid -> navigator.goTo(SaveCopyToLibrary(uuid)) },
                onOpenInLibrary = { uuid -> navigator.goTo(PdfViewer(uuid)) },
                showBackButton = true,
            )
        }
    }

    pdfDocumentDetailsSection(navigator)
    goToPageSection(navigator)
    saveCopyToLibrarySection(navigator)
}
