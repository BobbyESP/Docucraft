/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.details

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.navigation.Navigator
import com.bobbyesp.docucraft.core.presentation.navigation.overlay.LocalOverlayContext
import com.bobbyesp.docucraft.core.presentation.navigation.overlay.OverlayPresentation
import com.bobbyesp.docucraft.core.presentation.navigation.overlay.OverlaySceneStrategy
import com.bobbyesp.docucraft.feature.pdfviewer.domain.details.ViewerDocumentDetails
import com.bobbyesp.docucraft.feature.pdfviewer.domain.usecase.DocumentText
import com.bobbyesp.docucraft.feature.pdfviewer.navigation.PdfDocumentDetails
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The details of an open document, registered by both hosts that show documents: the app's shell
 * and `PdfViewerActivity`.
 *
 * A destination rather than a sheet the viewer keeps in a boolean, so [OverlaySceneStrategy] picks
 * a sheet or a dialog for the window, back closes it, and it survives rotation and process death
 * like any other entry (`docs/navigation.md`).
 */
fun EntryProviderScope<NavKey>.pdfDocumentDetailsSection(navigator: Navigator) {
    entry<PdfDocumentDetails>(metadata = OverlaySceneStrategy.overlay()) { key ->
        val viewModel: PdfDocumentDetailsViewModel = koinViewModel { parametersOf(key.document) }
        val state by viewModel.state.collectAsStateWithLifecycle()

        // This entry, not whatever is on top, which need not be this one.
        LaunchedEffect(state, key) {
            if (state is PdfDocumentDetailsState.Gone) navigator.removeDestination(key)
        }

        val details = (state as? PdfDocumentDetailsState.Ready)?.details ?: return@entry

        when (LocalOverlayContext.current.presentation) {
            OverlayPresentation.Sheet -> PdfDocumentDetailsSheetContent(details)

            OverlayPresentation.Dialog ->
                PdfDocumentDetailsDialog(
                    details = details,
                    onDismiss = navigator::goBack,
                    modifier = Modifier.widthIn(max = DialogMaxWidth),
                )
        }
    }
}

/** The sheet itself is the scene's; this is only what goes in it. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PdfDocumentDetailsSheetContent(details: ViewerDocumentDetails) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(
                    bottom =
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                            24.dp
                ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.document_details),
            style = MaterialTheme.typography.headlineSmallEmphasized,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        DetailRows(details)
    }
}

@Composable
private fun PdfDocumentDetailsDialog(
    details: ViewerDocumentDetails,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        icon = { Icon(imageVector = Icons.Rounded.Info, contentDescription = null) },
        title = {
            Text(text = stringResource(R.string.document_details), fontWeight = FontWeight.SemiBold)
        },
        text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { DetailRows(details) } },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.close)) }
        },
    )
}

@Composable
private fun DetailRows(details: ViewerDocumentDetails) {
    val context = LocalContext.current
    val unknown = "—"

    DetailRow(label = stringResource(R.string.name), value = details.name)
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    DetailRow(
        label = stringResource(R.string.page_count),
        value = details.pageCount?.toString() ?: unknown,
    )
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    DetailRow(
        label = stringResource(R.string.file_size),
        value = details.sizeBytes?.let { Formatter.formatShortFileSize(context, it) } ?: unknown,
    )
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    DetailRow(
        label = stringResource(R.string.document_text),
        value =
            when (details.text) {
                null -> stringResource(R.string.document_text_checking)
                DocumentText.Embedded -> stringResource(R.string.document_text_embedded)
                DocumentText.Recognized -> stringResource(R.string.document_text_recognized)
                DocumentText.None -> stringResource(R.string.document_text_none)
                DocumentText.Unsupported -> stringResource(R.string.document_text_unsupported)
                DocumentText.Unknown -> unknown
            },
    )
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    DetailRow(
        label = stringResource(R.string.description),
        value = details.description ?: stringResource(R.string.no_description),
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.6f),
        )
    }
}

private val DialogMaxWidth = 560.dp
