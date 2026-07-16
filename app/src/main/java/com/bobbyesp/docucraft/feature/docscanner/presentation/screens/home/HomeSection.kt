/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.navigation.Route
import com.bobbyesp.docucraft.feature.shared.domain.BasicDocument

/**
 * Home is the list pane of the app's list-detail layout: on expanded windows it stays visible next
 * to the open document, on compact windows it fills the screen.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.homeSection(
    selectedDocumentId: String?,
    onOpenDocument: (BasicDocument) -> Unit,
    onOpenSettings: () -> Unit,
) {
    entry<Route.Home>(
        metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { NoDocumentOpenPane() })
    ) {
        HomeScreen(
            onOpenDocument = onOpenDocument,
            onOpenSettings = onOpenSettings,
            selectedDocumentId = selectedDocumentId,
        )
    }
}

/** Shown in the detail pane on expanded windows while no document is open. */
@Composable
private fun NoDocumentOpenPane(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector = Icons.Rounded.FileOpen,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.select_document_to_preview),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
