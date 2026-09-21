/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.docucraft.core.presentation.navigation.Navigator
import com.bobbyesp.docucraft.core.presentation.navigation.pane.LocalPaneContext
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveDocumentUseCase
import com.bobbyesp.docucraft.feature.pdfviewer.navigation.PdfViewer
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.screens.PdfViewerScreen
import com.bobbyesp.docucraft.feature.shared.domain.BasicDocument
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject

/**
 * The PDF viewer is the detail pane of the list-detail layout: side by side with Home on expanded
 * windows, full screen on compact ones (where it shows its own back button). Which of the two it
 * got is read from the scene rather than measured off the window.
 *
 * The route names a document rather than carrying one, so this follows the catalogue instead of
 * reading it once. On expanded windows Home stays on screen next to the viewer, which means the
 * document can be renamed, or deleted, while it is open.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.pdfViewerSection(navigator: Navigator) {
    entry<PdfViewer>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
        val observeDocument: ObserveDocumentUseCase = koinInject()

        val document by
            remember(route.documentUuid) {
                    observeDocument(route.documentUuid).map(OpenDocument::of)
                }
                .collectAsStateWithLifecycle(initialValue = OpenDocument.Loading)

        // Only ever when the catalogue has actually said the document is gone, and only ever this
        // entry. `goBack` here would pop whatever is on top, which need not be the viewer.
        LaunchedEffect(document, route) {
            if (document is OpenDocument.Gone) navigator.removeDestination(route)
        }

        (document as? OpenDocument.Open)?.let { open ->
            PdfViewerScreen(
                documentInfo = open.document.toBasicDocument(),
                onBack = navigator::goBack,
                // Beside the list there is already a way back on screen; filling the window there
                // is not. The scene knows which of the two happened; this does not have to.
                showBackButton = LocalPaneContext.current.providesOwnBackAffordance,
            )
        }
    }
}

/**
 * What the catalogue has said so far about the document this entry points at.
 *
 * Three answers, because two of them used to be the same `null` and the difference between them is
 * the difference between waiting and leaving. Inferring it from whether a document had ever arrived
 * held only while the entry stayed in composition — and `NavDisplay` composes a scene afresh to
 * animate it back into view, so a predictive back woke the viewer on the flow's initial `null`.
 */
private sealed interface OpenDocument {

    /** No answer yet. The flow has been collected but has not emitted. */
    data object Loading : OpenDocument

    /** The catalogue says there is no such document, so this entry has nothing left to show. */
    data object Gone : OpenDocument

    data class Open(val document: ScannedDocument) : OpenDocument

    companion object {
        fun of(document: ScannedDocument?): OpenDocument = document?.let(::Open) ?: Gone
    }
}

/**
 * The viewer works from a [BasicDocument] because it also opens documents the app knows nothing
 * about, handed to it by other apps through [PdfViewerActivity].
 */
private fun ScannedDocument.toBasicDocument() =
    BasicDocument(
        uuid = uuid,
        filename = filename,
        uri = location.value,
        title = title,
        description = description,
    )
