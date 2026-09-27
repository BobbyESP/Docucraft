/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.toolbar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.domain.model.ViewerFitMode
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.TooltipIconButton
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.WithTooltip
import kotlin.math.roundToInt

/**
 * The viewer's bottom toolbar: the Material 3 Expressive floating toolbar, with the page, zoom, fit
 * mode and night mode. Design and reasons: `docs/pdf-viewer.md`.
 *
 * The zoom buttons appear only when the toolbar has room for them. Narrower, a zoom chip remains,
 * and only while the zoom is not the fitted one. That is the toolbar's own width, so it answers
 * correctly in a list-detail pane too.
 *
 * @param currentPage Zero-based.
 * @param zoom `1f` is the fitted size.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PdfViewerBottomToolbar(
    currentPage: Int,
    pageCount: Int,
    zoom: Float,
    canZoomIn: Boolean,
    canZoomOut: Boolean,
    fitMode: ViewerFitMode,
    nightMode: Boolean,
    onPageClick: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetZoom: () -> Unit,
    onFitModeChange: (ViewerFitMode) -> Unit,
    onNightModeToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val roomForZoomButtons = maxWidth >= WideToolbarWidth

        HorizontalFloatingToolbar(
            expanded = true,
            colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        ) {
            PageChip(currentPage = currentPage, pageCount = pageCount, onClick = onPageClick)

            if (roomForZoomButtons) {
                TooltipIconButton(
                    icon = Icons.Rounded.ZoomOut,
                    label = stringResource(R.string.zoom_out),
                    onClick = onZoomOut,
                    enabled = canZoomOut,
                    tooltipPosition = TooltipAnchorPosition.Above,
                )
                ZoomChip(zoom = zoom, onClick = onResetZoom)
                TooltipIconButton(
                    icon = Icons.Rounded.ZoomIn,
                    label = stringResource(R.string.zoom_in),
                    onClick = onZoomIn,
                    enabled = canZoomIn,
                    tooltipPosition = TooltipAnchorPosition.Above,
                )
            } else {
                AnimatedVisibility(
                    visible = !zoom.isFitted(),
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally(),
                ) {
                    ZoomChip(zoom = zoom, onClick = onResetZoom)
                }
            }

            FitModeButton(fitMode = fitMode, onFitModeChange = onFitModeChange)

            NightModeButton(nightMode = nightMode, onToggle = onNightModeToggle)
        }
    }
}

/** "3 / 12", animating as the page changes. Opens *Go to page*. */
@Composable
private fun PageChip(currentPage: Int, pageCount: Int, onClick: () -> Unit) {
    val description =
        stringResource(R.string.page_indicator_description, currentPage + 1, pageCount)
    WithTooltip(
        label = stringResource(R.string.go_to_page),
        position = TooltipAnchorPosition.Above,
    ) {
        TextButton(
            onClick = onClick,
            modifier = Modifier.semantics { contentDescription = description },
        ) {
            AnimatedContent(
                targetState = currentPage + 1,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "page",
            ) { page ->
                Text(text = "$page / $pageCount", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** The zoom as a percentage; tapping it goes back to the fitted size. */
@Composable
private fun ZoomChip(zoom: Float, onClick: () -> Unit) {
    val percent = (zoom * 100).roundToInt()
    val description = stringResource(R.string.zoom_level, percent)
    WithTooltip(
        label = stringResource(R.string.reset_zoom),
        position = TooltipAnchorPosition.Above,
    ) {
        TextButton(
            onClick = onClick,
            modifier = Modifier.semantics { contentDescription = description },
        ) {
            Text(text = "$percent%", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** An icon for the current fit mode, opening a menu of all four by name. */
@Composable
private fun FitModeButton(fitMode: ViewerFitMode, onFitModeChange: (ViewerFitMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = stringResource(R.string.fit_mode)
    val current = stringResource(fitMode.label)

    Box {
        TooltipIconButton(
            icon = fitMode.icon(),
            label = label,
            onClick = { expanded = true },
            modifier = Modifier.semantics { stateDescription = current },
            tooltipPosition = TooltipAnchorPosition.Above,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ViewerFitMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(stringResource(mode.label)) },
                    leadingIcon = { Icon(mode.icon(), contentDescription = null) },
                    trailingIcon = {
                        if (mode == fitMode) Icon(Icons.Rounded.Check, contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        onFitModeChange(mode)
                    },
                )
            }
        }
    }
}

/** A toggle, with the Expressive shape change when it is on. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NightModeButton(nightMode: Boolean, onToggle: () -> Unit) {
    val label = stringResource(R.string.night_mode)
    WithTooltip(label = label, position = TooltipAnchorPosition.Above) {
        FilledTonalIconToggleButton(
            checked = nightMode,
            onCheckedChange = { onToggle() },
            shapes = IconButtonDefaults.toggleableShapes(),
        ) {
            Icon(imageVector = Icons.Rounded.DarkMode, contentDescription = label)
        }
    }
}

private fun Float.isFitted(): Boolean = kotlin.math.abs(this - 1f) < 0.02f

private val ViewerFitMode.label: Int
    get() =
        when (this) {
            ViewerFitMode.WIDTH -> R.string.fit_mode_width
            ViewerFitMode.HEIGHT -> R.string.fit_mode_height
            ViewerFitMode.BOTH -> R.string.fit_mode_page
            ViewerFitMode.PROPORTIONAL -> R.string.fit_mode_proportional
        }

@Composable
private fun ViewerFitMode.icon(): ImageVector =
    when (this) {
        ViewerFitMode.WIDTH -> ImageVector.vectorResource(R.drawable.fit_page_width)
        ViewerFitMode.HEIGHT -> ImageVector.vectorResource(R.drawable.fit_page_height)
        ViewerFitMode.BOTH -> Icons.Rounded.FitScreen
        ViewerFitMode.PROPORTIONAL -> ImageVector.vectorResource(R.drawable.fit_page)
    }

/** From here the zoom buttons fit without crowding the rest: Material's medium width. */
private val WideToolbarWidth = 600.dp

@PreviewLightDark
@Composable
private fun PdfViewerBottomToolbarPreview() {
    DocucraftTheme {
        PdfViewerBottomToolbar(
            currentPage = 2,
            pageCount = 12,
            zoom = 1.4f,
            canZoomIn = true,
            canZoomOut = true,
            fitMode = ViewerFitMode.WIDTH,
            nightMode = false,
            onPageClick = {},
            onZoomIn = {},
            onZoomOut = {},
            onResetZoom = {},
            onFitModeChange = {},
            onNightModeToggle = {},
        )
    }
}
