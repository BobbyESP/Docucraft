/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.toolbar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.feature.shared.domain.BasicDocument

/**
 * Floating "pill" top bar for the PDF viewer, following Material 3 Expressive guidelines.
 *
 * Visually cohesive with [PdfViewerBottomToolbar]: a rounded, tonal [Surface] that floats over the
 * document rather than spanning the screen edge-to-edge. It exposes the document title/subtitle and the
 * document-level actions (share, print, open-with, details).
 *
 * @param documentInfo The document currently being viewed.
 * @param pageCount Total page count, used to build the subtitle when no description is available.
 * @param showBackButton Whether to show the leading back button.
 * @param onBack Invoked when the back button is tapped.
 * @param onShare Invoked to share the document.
 * @param onPrint Invoked to print the document.
 * @param onOpenWith Invoked to open the document in another app.
 * @param onDetails Invoked to show the document details sheet.
 * @param modifier Optional modifier for the bar container.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PdfViewerTopBar(
    documentInfo: BasicDocument,
    pageCount: Int,
    showBackButton: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    onOpenWith: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subtitle =
        documentInfo.description
            ?: if (pageCount > 0) stringResource(R.string.pages_count, pageCount)
            else stringResource(R.string.no_description)

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBackButton) {
                IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cancel),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = documentInfo.title ?: documentInfo.filename,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    modifier = Modifier.alpha(0.66f),
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMediumEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // The single emphasized action on the bar.
            FilledIconButton(onClick = onShare, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = stringResource(R.string.share),
                )
            }

            OverflowMenu(onPrint = onPrint, onOpenWith = onOpenWith, onDetails = onDetails)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OverflowMenu(onPrint: () -> Unit, onOpenWith: () -> Unit, onDetails: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }, shapes = IconButtonDefaults.shapes()) {
        Icon(
            imageVector = Icons.Rounded.MoreVert,
            contentDescription = stringResource(R.string.more_options),
        )
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.print)) },
            onClick = {
                expanded = false
                onPrint()
            },
            leadingIcon = { Icon(Icons.Rounded.Print, contentDescription = null) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.open_with)) },
            onClick = {
                expanded = false
                onOpenWith()
            },
            leadingIcon = {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.document_details)) },
            onClick = {
                expanded = false
                onDetails()
            },
            leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
        )
    }
}
