/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.docucraft.core.presentation.navigation.Route
import com.bobbyesp.docucraft.core.presentation.navigation.isMultiPaneLayout
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.screens.PdfViewerScreen

/**
 * The PDF viewer is the detail pane of the list-detail layout: side by side with Home when the
 * window is wide enough for two panes, full screen otherwise (where it shows its own back button).
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.pdfViewerSection(onBack: () -> Unit) {
    entry<Route.PdfViewer>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
        // Whether a back button is needed depends on whether the list is actually on screen next
        // to us, which is a pane-count question rather than a width one. Two panes require an
        // *expanded* window (840dp+); a medium 600–839dp window still lays this out full screen,
        // and deciding on the medium breakpoint left it with no back affordance at all there.
        PdfViewerScreen(
            documentInfo = route.document,
            onBack = onBack,
            showBackButton = !isMultiPaneLayout,
        )
    }
}
