/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.toolbar

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.core.designsystem.theme.DocucraftBlurDefaults
import com.bobbyesp.docucraft.core.designsystem.theme.frosted
import dev.chrisbanes.haze.HazeState

/**
 * The Expressive vibrant floating toolbar, frosted in its color over the pages instead of lifted
 * from them by a shadow: they scroll on beneath it, blurred.
 *
 * @param hazeState Where the pages are recorded.
 */
@Composable
internal fun FrostedFloatingToolbar(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val container = FloatingToolbarDefaults.vibrantFloatingToolbarColors().toolbarContainerColor
    val shape = FloatingToolbarDefaults.ContainerShape
    HorizontalFloatingToolbar(
        expanded = true,
        modifier =
            modifier.frosted(
                state = hazeState,
                style = DocucraftBlurDefaults.surfaceStyle(container),
                shape = shape,
            ),
        colors =
            FloatingToolbarDefaults.vibrantFloatingToolbarColors(
                toolbarContainerColor = Color.Transparent
            ),
        shape = shape,
        expandedShadowElevation = 0.dp,
        collapsedShadowElevation = 0.dp,
        content = content,
    )
}
