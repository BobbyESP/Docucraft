/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.docucraft.core.presentation.common.LocalWindowWidthState
import com.bobbyesp.docucraft.core.presentation.navigation.Route
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.screens.PdfViewerScreen

/**
 * The PDF viewer is the detail pane of the list-detail layout: side by side with Home on expanded
 * windows, full screen on compact ones (where it shows its own back button).
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.pdfViewerSection(onBack: () -> Unit) {
    entry<Route.PdfViewer>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
        val isCompact = LocalWindowWidthState.current == WindowWidthSizeClass.Compact

        PdfViewerScreen(
            documentInfo = route.document,
            onBack = onBack,
            showBackButton = isCompact,
        )
    }
}
