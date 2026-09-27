/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.search

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.docucraft.core.presentation.navigation.Navigator
import com.bobbyesp.docucraft.core.presentation.navigation.motion.SharedElementMotion
import com.bobbyesp.docucraft.feature.docscanner.navigation.DocumentActions
import com.bobbyesp.docucraft.feature.docscanner.navigation.DocumentSearch
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.NoDocumentOpenPane
import com.bobbyesp.docucraft.feature.pdfviewer.navigation.PdfViewer

/**
 * Search is a list like Home, and on wide windows takes Home's place beside the open document, so a
 * result opens next to the results rather than over them.
 *
 * It arrives through Home's search bar, hence [SharedElementMotion] instead of the door slide.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.documentSearchSection(
    navigator: Navigator,
    selectedDocumentId: String?,
) {
    entry<DocumentSearch>(
        metadata =
            ListDetailSceneStrategy.listPane(detailPlaceholder = { NoDocumentOpenPane() }) +
                SharedElementMotion.metadata()
    ) {
        DocumentSearchScreen(
            onBack = navigator::goBack,
            onOpenDocument = { uuid ->
                // As from Home, a result replaces the document open beside the list. Only
                // viewers go, though: search itself stays, so back returns to the results.
                navigator.goBackWhile { it is PdfViewer }
                navigator.goTo(PdfViewer(uuid))
            },
            onOpenDocumentActions = { uuid -> navigator.goTo(DocumentActions(uuid)) },
            selectedDocumentId = selectedDocumentId,
        )
    }
}
