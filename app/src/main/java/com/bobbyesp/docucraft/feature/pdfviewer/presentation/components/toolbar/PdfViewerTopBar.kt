/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.toolbar

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AppBarRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.TooltipIconButton

/**
 * The viewer's top app bar: a standard Material `TopAppBar`, with the document's name, its
 * description as the subtitle when it has one, and its actions.
 *
 * The actions go in an [AppBarRow], which shows as many as fit and moves the rest into its overflow
 * menu: a phone shows Share and "more", a wide pane shows them all. The bar adapts to the room it
 * is given without the screen having to measure the window. Design and reasons:
 * `docs/architecture/09-pdfviewer-ui-design.md`.
 *
 * @param onShare `null` when the document cannot leave the app; the action is then left out.
 * @param onOpenWith Likewise.
 * @param onPrint `null` when there is nothing to print: the document did not load.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PdfViewerTopBar(
    title: String,
    description: String?,
    showBackButton: Boolean,
    onBack: () -> Unit,
    onShare: (() -> Unit)?,
    onPrint: (() -> Unit)?,
    onOpenWith: (() -> Unit)?,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors =
        TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    val titleContent: @Composable () -> Unit = {
        Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    val navigationIcon: @Composable () -> Unit = {
        if (showBackButton) {
            TooltipIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                label = stringResource(R.string.back),
                onClick = onBack,
            )
        }
    }

    val shareLabel = stringResource(R.string.share)
    val printLabel = stringResource(R.string.print)
    val openWithLabel = stringResource(R.string.open_with)
    val detailsLabel = stringResource(R.string.document_details)
    val moreLabel = stringResource(R.string.more_options)

    val actions: @Composable (maxItems: Int) -> Unit = { maxItems ->
        AppBarRow(
            maxItemCount = maxItems,
            overflowIndicator = { menuState ->
                TooltipIconButton(
                    icon = Icons.Rounded.MoreVert,
                    label = moreLabel,
                    onClick = { menuState.show() },
                )
            },
        ) {
            if (onShare != null) {
                clickableItem(
                    onClick = onShare,
                    icon = { Icon(Icons.Rounded.Share, contentDescription = shareLabel) },
                    label = shareLabel,
                )
            }
            if (onPrint != null) {
                clickableItem(
                    onClick = onPrint,
                    icon = { Icon(Icons.Rounded.Print, contentDescription = printLabel) },
                    label = printLabel,
                )
            }
            if (onOpenWith != null) {
                clickableItem(
                    onClick = onOpenWith,
                    icon = {
                        Icon(
                            Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = openWithLabel,
                        )
                    },
                    label = openWithLabel,
                )
            }
            clickableItem(
                onClick = onDetails,
                icon = { Icon(Icons.Rounded.Info, contentDescription = detailsLabel) },
                label = detailsLabel,
            )
        }
    }

    // The bar's own width, not the window's: in a list-detail layout the viewer is one pane. A
    // narrow
    // bar keeps its title legible by showing only Share and the overflow menu.
    BoxWithConstraints(modifier = modifier) {
        // The overflow button counts as an item: 2 is Share plus "more".
        val maxItems = if (maxWidth < WideBarWidth) 2 else Int.MAX_VALUE
        TopBar(description, titleContent, navigationIcon, { actions(maxItems) }, colors)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TopBar(
    description: String?,
    titleContent: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    colors: TopAppBarColors,
) {
    if (description.isNullOrBlank()) {
        TopAppBar(
            title = titleContent,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = colors,
        )
    } else {
        TopAppBar(
            title = titleContent,
            subtitle = { Text(text = description, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = navigationIcon,
            actions = actions,
            colors = colors,
        )
    }
}

/** Below this, only Share and the overflow menu: the Material compact width. */
private val WideBarWidth = 600.dp

@PreviewLightDark
@Composable
private fun PdfViewerTopBarPreview() {
    DocucraftTheme {
        PdfViewerTopBar(
            title = "Invoice March",
            description = "Paid on 12/03",
            showBackButton = true,
            onBack = {},
            onShare = {},
            onPrint = {},
            onOpenWith = {},
            onDetails = {},
        )
    }
}
