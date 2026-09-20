/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.window.core.layout.WindowSizeClass
import com.bobbyesp.docucraft.core.presentation.navigation.Navigator
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveDocumentUseCase
import com.bobbyesp.docucraft.feature.pdfviewer.navigation.PdfViewer
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.screens.PdfViewerScreen
import com.bobbyesp.docucraft.feature.shared.domain.BasicDocument
import org.koin.compose.koinInject

/**
 * The PDF viewer is the detail pane of the list-detail layout: side by side with Home on expanded
 * windows, full screen on compact ones (where it shows its own back button).
 *
 * The route names a document rather than carrying one, so this follows the catalogue instead of
 * reading it once. On expanded windows Home stays on screen next to the viewer, which means the
 * document can be renamed, or deleted, while it is open.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.pdfViewerSection(navigator: Navigator) {
    entry<PdfViewer>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
        val onBack = navigator::goBack

        val observeDocument: ObserveDocumentUseCase = koinInject()

        val document by
            remember(route.documentUuid) { observeDocument(route.documentUuid) }
                .collectAsStateWithLifecycle(initialValue = null)

        // Null means "not read yet" at first and "deleted" afterwards, and the two call for
        // opposite reactions: wait, then leave.
        var wasLoaded by rememberSaveable(route.documentUuid) { mutableStateOf(false) }

        LaunchedEffect(document) {
            if (document != null) wasLoaded = true else if (wasLoaded) onBack()
        }

        document?.let { scannedDocument ->
            val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
            val isCompact =
                !windowSizeClass.isWidthAtLeastBreakpoint(
                    WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND
                )

            PdfViewerScreen(
                documentInfo = scannedDocument.toBasicDocument(),
                onBack = onBack,
                showBackButton = isCompact,
            )
        }
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
