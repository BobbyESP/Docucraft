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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.feature.shared.domain.BasicDocument
import com.composepdf.FitMode

/**
 * Floating top bar for the PDF viewer.
 *
 * A rounded surface that floats over the document rather than spanning the screen edge to edge, so
 * it reads as chrome laid on the page instead of a header the page is pushed beneath. It carries
 * identity (which document, how far in) and the document-level actions; everything infrequent lives
 * behind the overflow with a written label, including the fit mode that used to cycle blindly
 * behind a single unlabelled icon.
 */
@Composable
fun PdfViewerTopBar(
    documentInfo: BasicDocument,
    currentPage: Int,
    pageCount: Int,
    showBackButton: Boolean,
    fitMode: FitMode,
    isNightModeEnabled: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    onOpenWith: () -> Unit,
    onDetails: () -> Unit,
    onFitModeChange: (FitMode) -> Unit,
    onNightModeToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        // Neutral rather than primaryContainer: the page is the content, and a slab of primary
        // across the top competes with it.
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBackButton) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cancel),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = documentInfo.title ?: documentInfo.filename,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (pageCount > 0) {
                    Text(
                        text =
                            stringResource(
                                R.string.page_of,
                                (currentPage + 1).toString(),
                                pageCount.toString(),
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        // A real colour role rather than an alpha multiplier, which used to push
                        // this below the contrast floor.
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            IconButton(onClick = onShare) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = stringResource(R.string.share),
                )
            }

            ViewerOverflowMenu(
                fitMode = fitMode,
                isNightModeEnabled = isNightModeEnabled,
                onPrint = onPrint,
                onOpenWith = onOpenWith,
                onDetails = onDetails,
                onFitModeChange = onFitModeChange,
                onNightModeToggle = onNightModeToggle,
            )
        }
    }
}

@Composable
private fun ViewerOverflowMenu(
    fitMode: FitMode,
    isNightModeEnabled: Boolean,
    onPrint: () -> Unit,
    onOpenWith: () -> Unit,
    onDetails: () -> Unit,
    onFitModeChange: (FitMode) -> Unit,
    onNightModeToggle: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }) {
        Icon(
            imageVector = Icons.Rounded.MoreVert,
            contentDescription = stringResource(R.string.more_options),
        )
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Night mode stays open so it can be judged against the page behind the menu.
        DropdownMenuItem(
            text = { Text(stringResource(R.string.night_mode)) },
            onClick = onNightModeToggle,
            leadingIcon = { Icon(Icons.Rounded.DarkMode, contentDescription = null) },
            trailingIcon = {
                Switch(checked = isNightModeEnabled, onCheckedChange = { onNightModeToggle() })
            },
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Text(
            text = stringResource(R.string.fit_mode),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
        )

        FitMode.entries.forEach { mode ->
            val selected = mode == fitMode
            DropdownMenuItem(
                text = { Text(mode.label()) },
                onClick = {
                    expanded = false
                    onFitModeChange(mode)
                },
                trailingIcon = {
                    // A check on the active mode, so the current fit is visible instead of being
                    // something you infer by cycling.
                    if (selected) Icon(Icons.Rounded.Check, contentDescription = null)
                },
            )
        }
    }
}

@Composable
private fun FitMode.label(): String =
    when (this) {
        FitMode.WIDTH -> stringResource(R.string.fit_width)
        FitMode.HEIGHT -> stringResource(R.string.fit_height)
        FitMode.BOTH -> stringResource(R.string.fit_page)
        FitMode.PROPORTIONAL -> stringResource(R.string.fit_proportional)
    }
