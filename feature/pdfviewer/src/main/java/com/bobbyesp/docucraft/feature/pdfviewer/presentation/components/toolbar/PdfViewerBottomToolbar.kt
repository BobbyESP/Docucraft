/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

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
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
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
import com.bobbyesp.docucraft.core.designsystem.R as DesignsystemR
import com.bobbyesp.docucraft.core.designsystem.components.FrostedMenuGroup
import com.bobbyesp.docucraft.core.designsystem.theme.DocucraftTheme
import com.bobbyesp.docucraft.core.domain.model.ViewerFitMode
import com.bobbyesp.docucraft.feature.pdfviewer.R
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.TooltipIconButton
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.WithTooltip
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The viewer's bottom toolbar: page, zoom, fit mode and night mode.
 *
 * The zoom buttons need the toolbar's own width, not the window's, so it answers correctly in a
 * list-detail pane too. Narrower, a zoom chip shows only while the zoom is not the fitted one.
 *
 * @param currentPage Zero-based.
 * @param zoom `1f` is the fitted size.
 * @param hazeState Where the pages are recorded.
 */
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
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val roomForZoomButtons = maxWidth >= WideToolbarWidth

        FrostedFloatingToolbar(hazeState = hazeState) {
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
                    enter =
                        fadeIn(motion.fastEffectsSpec()) +
                            expandHorizontally(motion.fastSpatialSpec()),
                    exit =
                        fadeOut(motion.fastEffectsSpec()) +
                            shrinkHorizontally(motion.fastSpatialSpec()),
                ) {
                    ZoomChip(zoom = zoom, onClick = onResetZoom)
                }
            }

            FitModeButton(
                fitMode = fitMode,
                onFitModeChange = onFitModeChange,
                hazeState = hazeState,
            )

            NightModeButton(nightMode = nightMode, onToggle = onNightModeToggle)
        }
    }
}

/** "3 / 12", animating as the page changes. Opens *Go to page*. */
@Composable
private fun PageChip(currentPage: Int, pageCount: Int, onClick: () -> Unit) {
    val motion = MaterialTheme.motionScheme
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
                transitionSpec = {
                    fadeIn(motion.fastEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec())
                },
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

/**
 * The current fit mode's icon, opening a menu of the four by name. Frosted over the pages and with
 * a shadow rather than a blur halo, as the top bar's menu, for the same reason: the toolbar is not
 * in the pages' recording, and a halo would draw the pages over it.
 */
@Composable
private fun FitModeButton(
    fitMode: ViewerFitMode,
    onFitModeChange: (ViewerFitMode) -> Unit,
    hazeState: HazeState,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = stringResource(fitMode.label)
    val modes = ViewerFitMode.entries

    Box {
        TooltipIconButton(
            icon = fitMode.icon(),
            label = stringResource(DesignsystemR.string.fit_mode),
            onClick = { expanded = true },
            modifier = Modifier.semantics { stateDescription = current },
            tooltipPosition = TooltipAnchorPosition.Above,
        )
        DropdownMenuPopup(expanded = expanded, onDismissRequest = { expanded = false }) {
            FrostedMenuGroup(
                shapes = MenuDefaults.groupShape(index = 0, count = 1),
                hazeState = hazeState,
                shadowElevation = MenuDefaults.ShadowElevation,
            ) {
                modes.forEachIndexed { index, mode ->
                    SelectableDropdownMenuItem(
                        selected = mode == fitMode,
                        onClick = {
                            expanded = false
                            onFitModeChange(mode)
                        },
                        text = { Text(stringResource(mode.label)) },
                        shapes = MenuDefaults.itemShape(index = index, count = modes.size),
                        leadingIcon = { Icon(mode.icon(), contentDescription = null) },
                        trailingContent = {
                            if (mode == fitMode) {
                                Icon(Icons.Rounded.Check, contentDescription = null)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun NightModeButton(nightMode: Boolean, onToggle: () -> Unit) {
    val label = stringResource(DesignsystemR.string.night_mode)
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

private fun Float.isFitted(): Boolean = abs(this - 1f) < FittedTolerance

private val ViewerFitMode.label: Int
    get() =
        when (this) {
            ViewerFitMode.WIDTH -> DesignsystemR.string.fit_mode_width
            ViewerFitMode.HEIGHT -> DesignsystemR.string.fit_mode_height
            ViewerFitMode.BOTH -> DesignsystemR.string.fit_mode_page
            ViewerFitMode.PROPORTIONAL -> DesignsystemR.string.fit_mode_proportional
        }

@Composable
private fun ViewerFitMode.icon(): ImageVector =
    when (this) {
        ViewerFitMode.WIDTH -> ImageVector.vectorResource(DesignsystemR.drawable.fit_page_width)
        ViewerFitMode.HEIGHT -> ImageVector.vectorResource(DesignsystemR.drawable.fit_page_height)
        ViewerFitMode.BOTH -> Icons.Rounded.FitScreen
        ViewerFitMode.PROPORTIONAL -> ImageVector.vectorResource(DesignsystemR.drawable.fit_page)
    }

/** Material's medium width: from here the zoom buttons fit without crowding the rest. */
private val WideToolbarWidth = 600.dp

private const val FittedTolerance = 0.02f

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
            hazeState = rememberHazeState(),
        )
    }
}
