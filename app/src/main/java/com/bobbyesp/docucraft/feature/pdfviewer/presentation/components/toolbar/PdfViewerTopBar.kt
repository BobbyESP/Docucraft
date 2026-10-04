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
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.components.FrostedMenuGroup
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftBlurDefaults
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
import com.bobbyesp.docucraft.core.presentation.theme.frosted
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.TooltipIconButton
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState

/**
 * The viewer's top app bar: a standard Material `TopAppBar`, with the document's name, its
 * description as the subtitle when it has one, and its actions.
 *
 * As many actions as fit are buttons and the rest go in a menu: a phone shows one and "more", a
 * wide pane shows them all. The bar adapts to the room it is given without the screen having to
 * measure the window. Design and reasons: `docs/pdf-viewer.md`.
 *
 * Frosted over the pages, which scroll beneath it: the document stays in view under the bar,
 * blurred, instead of ending at its edge.
 *
 * @param hazeState Where the pages are recorded.
 * @param onSaveToLibrary Keeps another app's document in the library; `null` for a document that is
 *   already the app's, and the action is then left out. It comes first, so that it is the one
 *   action a narrow bar shows: it is the only way to keep a document that is otherwise on loan.
 * @param isSavingToLibrary The copy is under way; the action stays but does nothing.
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
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    onSaveToLibrary: (() -> Unit)? = null,
    isSavingToLibrary: Boolean = false,
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
                label = stringResource(R.string.back),
                onClick = onBack,
            )
        }
    }

    // In the order they are shown: what there is room for as buttons, the rest in the menu.
    // Details last and apart, since it is about the document rather than something done with it.
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
                ViewerAction(Icons.Rounded.Share, stringResource(R.string.share), onClick = it)
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

    // The bar's own width, not the window's: in a list-detail layout the viewer is one pane. A
    // narrow bar keeps its title legible by showing one action and the menu.
    BoxWithConstraints(modifier = modifier.frosted(state = hazeState, style = frostedStyle)) {
        val shown = if (maxWidth < WideBarWidth) NarrowBarActions else Int.MAX_VALUE
        TopBar(
            description = description,
            titleContent = titleContent,
            navigationIcon = navigationIcon,
            actions = {
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
 * The bar's actions: the first [shown] as buttons, and the rest in a menu behind "more". The menu
 * is the app's own, as Home's sort menu is: groups frosted over the pages it opens on. What is done
 * with the document is one group and what it is, the details, another.
 *
 * @param hazeState Where the pages the menu opens over are recorded.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ViewerActions(
    handOff: List<ViewerAction>,
    details: ViewerAction,
    shown: Int,
    hazeState: HazeState,
) {
    val all = handOff + details
    val buttons = if (all.size <= shown) all else all.take(shown)
    val inMenu = all.drop(buttons.size)

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
    val groups = listOf(inMenu.filter { it !== details }, inMenu.filter { it === details })
    val menuGroups = groups.filter { it.isNotEmpty() }

    Box {
        TooltipIconButton(
            icon = Icons.Rounded.MoreVert,
            label = stringResource(R.string.more_options),
            onClick = { expanded = true },
        )

        // Material's popup, not the one with a halo: a halo blurs what the pages recorded all
        // around the menu, and this menu opens from the bar, which is not in that recording. The
        // halo painted the pages over the bar's own buttons. The groups cast a shadow instead.
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
