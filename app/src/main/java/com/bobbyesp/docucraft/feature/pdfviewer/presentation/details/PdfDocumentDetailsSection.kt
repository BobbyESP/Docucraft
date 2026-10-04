/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.details

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.components.overlay.OverlayForm
import com.bobbyesp.docucraft.core.presentation.navigation.Navigator
import com.bobbyesp.docucraft.core.presentation.navigation.overlay.OverlaySceneStrategy
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftShapeDefaults
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
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
 * like any other entry (`docs/navigation.md`). [OverlayForm] gives it the same heading and close
 * button in either, as every other overlay of the app has.
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

        PdfDocumentDetailsContent(details = details, onDismiss = navigator::goBack)
    }
}

/**
 * What is known of the document: its name and description as the heading, and each fact as a tile
 * of its own, the value large and what it measures small under it.
 *
 * A fact that is not known is left out rather than shown as a dash: a document of another app may
 * have neither a size nor a page count to give. Whether it has text is always said, since "none" is
 * an answer the reader can act on.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PdfDocumentDetailsContent(
    details: ViewerDocumentDetails,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme

    OverlayForm(
        title = details.name,
        icon = Icons.Rounded.Description,
        onDismiss = onDismiss,
        modifier = modifier,
        description = details.description?.takeIf { it.isNotBlank() },
        dismissText = stringResource(R.string.close),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (details.pageCount != null || details.sizeBytes != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    details.pageCount?.let { pages ->
                        FactTile(
                            icon = Icons.Rounded.FileCopy,
                            value = pages.toString(),
                            label = stringResource(R.string.page_count),
                            containerColor = colorScheme.primaryContainer,
                            contentColor = colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    details.sizeBytes?.let { size ->
                        FactTile(
                            icon = Icons.Rounded.Storage,
                            value = Formatter.formatShortFileSize(context, size),
                            label = stringResource(R.string.file_size),
                            containerColor = colorScheme.secondaryContainer,
                            contentColor = colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
            TextFact(text = details.text)
        }
    }
}

/** One fact about the document, in a container of its own color. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FactTile(
    icon: ImageVector,
    value: String,
    label: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = DocucraftShapeDefaults.cardShape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * Whether the document has text to select and search, which takes reading some of its pages: said
 * as being looked into until it is known.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TextFact(text: DocumentText?, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = DocucraftShapeDefaults.cardShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                if (text == null) {
                    LoadingIndicator(
                        modifier = Modifier.size(24.dp),
                        color = LocalContentColor.current,
                    )
                } else {
                    Icon(imageVector = Icons.Rounded.TextFields, contentDescription = null)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.document_text),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text =
                        when (text) {
                            null -> stringResource(R.string.document_text_checking)
                            DocumentText.Embedded -> stringResource(R.string.document_text_embedded)
                            DocumentText.Recognized ->
                                stringResource(R.string.document_text_recognized)
                            DocumentText.None -> stringResource(R.string.document_text_none)
                            DocumentText.Unsupported ->
                                stringResource(R.string.document_text_unsupported)
                            DocumentText.Unknown -> stringResource(R.string.document_text_unknown)
                        },
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun PdfDocumentDetailsContentPreview() {
    DocucraftTheme {
        Surface {
            PdfDocumentDetailsContent(
                details =
                    ViewerDocumentDetails(
                        name = "Invoice March",
                        description = "Paid on 12/03",
                        pageCount = 3,
                        sizeBytes = 184_320,
                        text = DocumentText.Embedded,
                    ),
                onDismiss = {},
            )
        }
    }
}
