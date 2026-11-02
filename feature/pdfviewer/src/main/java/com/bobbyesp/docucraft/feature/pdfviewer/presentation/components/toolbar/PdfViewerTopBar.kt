/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.toolbar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.core.designsystem.R as DesignsystemR
import com.bobbyesp.docucraft.core.designsystem.components.FrostedMenuGroup
import com.bobbyesp.docucraft.core.designsystem.theme.DocucraftBlurDefaults
import com.bobbyesp.docucraft.core.designsystem.theme.DocucraftTheme
import com.bobbyesp.docucraft.core.designsystem.theme.frosted
import com.bobbyesp.docucraft.feature.pdfviewer.R
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.TooltipIconButton
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState

/**
 * The viewer's top app bar, frosted over the pages that scroll beneath it.
 *
 * As many actions as the bar's own width allows are buttons and the rest go in a menu: a phone
 * shows one and "more", a wide pane all of them. See `docs/pdf-viewer.md`.
 *
 * A `null` action is left out.
 *
 * @param hazeState Where the pages are recorded.
 * @param onSaveToLibrary For another app's document. First, so that it is the one action a narrow
 *   bar shows: without it the document is only on loan.
 * @param isSavingToLibrary The copy is under way; the action stays but is disabled.
 * @param onSearch Always a button of its own, never in the menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    onSaveToLibrary: (() -> Unit)? = null,
    isSavingToLibrary: Boolean = false,
    onSearch: (() -> Unit)? = null,
) {
    val frostedStyle =
        DocucraftBlurDefaults.surfaceStyle(MaterialTheme.colorScheme.surfaceContainer)
    val colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
    val titleContent: @Composable () -> Unit = {
        Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    val navigationIcon: @Composable () -> Unit = {
        if (showBackButton) {
            TooltipIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                label = stringResource(DesignsystemR.string.back),
                onClick = onBack,
            )
        }
    }

    // Details last and apart: it is about the document, not something done with it.
    val handOff =
        listOfNotNull(
            onSaveToLibrary?.let {
                ViewerAction(
                    icon = Icons.Rounded.LibraryAdd,
                    label = stringResource(R.string.save_to_docucraft),
                    enabled = !isSavingToLibrary,
                    onClick = it,
                )
            },
            onShare?.let {
                ViewerAction(
                    Icons.Rounded.Share,
                    stringResource(DesignsystemR.string.share),
                    onClick = it,
                )
            },
            onPrint?.let {
                ViewerAction(Icons.Rounded.Print, stringResource(R.string.print), onClick = it)
            },
            onOpenWith?.let {
                ViewerAction(
                    icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    label = stringResource(R.string.open_with),
                    onClick = it,
                )
            },
        )
    val details =
        ViewerAction(
            icon = Icons.Rounded.Info,
            label = stringResource(R.string.document_details),
            onClick = onDetails,
        )

    BoxWithConstraints(modifier = modifier.frosted(state = hazeState, style = frostedStyle)) {
        val shown = if (maxWidth < WideBarWidth) NarrowBarActions else Int.MAX_VALUE
        TopBar(
            description = description,
            titleContent = titleContent,
            navigationIcon = navigationIcon,
            actions = {
                onSearch?.let {
                    TooltipIconButton(
                        icon = Icons.Rounded.Search,
                        label = stringResource(R.string.search_in_document),
                        onClick = it,
                    )
                }
                ViewerActions(
                    handOff = handOff,
                    details = details,
                    shown = shown,
                    hazeState = hazeState,
                )
            },
            colors = colors,
        )
    }
}

/** Something the bar offers to do with the document. */
@Immutable
private class ViewerAction(
    val icon: ImageVector,
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * The first [shown] actions as buttons, the rest in a menu of frosted groups: what is done with the
 * document, and its details. It has a shadow, not a blur halo: the halo blurs the pages' recording,
 * and the bar is not in it, so it drew the pages over the bar's buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewerActions(
    handOff: List<ViewerAction>,
    details: ViewerAction,
    shown: Int,
    hazeState: HazeState,
) {
    val all = handOff + details
    val buttons = all.take(shown)
    val inMenu = all.drop(shown)

    for (action in buttons) {
        TooltipIconButton(
            icon = action.icon,
            label = action.label,
            onClick = action.onClick,
            enabled = action.enabled,
        )
    }
    if (inMenu.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    val menuGroups = inMenu.partition { it !== details }.toList().filter { it.isNotEmpty() }

    Box {
        TooltipIconButton(
            icon = Icons.Rounded.MoreVert,
            label = stringResource(DesignsystemR.string.more_options),
            onClick = { expanded = true },
        )

        DropdownMenuPopup(expanded = expanded, onDismissRequest = { expanded = false }) {
            menuGroups.forEachIndexed { groupIndex, group ->
                if (groupIndex > 0) Spacer(modifier = Modifier.height(MenuDefaults.GroupSpacing))
                FrostedMenuGroup(
                    shapes = MenuDefaults.groupShape(index = groupIndex, count = menuGroups.size),
                    hazeState = hazeState,
                    shadowElevation = MenuDefaults.ShadowElevation,
                ) {
                    group.forEachIndexed { index, action ->
                        DropdownMenuItem(
                            onClick = {
                                expanded = false
                                action.onClick()
                            },
                            text = { Text(text = action.label) },
                            shape = MenuDefaults.itemShape(index = index, count = group.size).shape,
                            leadingIcon = { Icon(action.icon, contentDescription = null) },
                            enabled = action.enabled,
                        )
                    }
                }
            }
        }
    }
}

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

/** Below this, one action and the menu: the Material compact width. */
private val WideBarWidth = 600.dp

/** How many actions a narrow bar shows as buttons, beside the menu. */
private const val NarrowBarActions = 1

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
            hazeState = rememberHazeState(),
        )
    }
}
