/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuGroupShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftBlurDefaults
import com.bobbyesp.docucraft.core.presentation.theme.frosted
import dev.chrisbanes.haze.HazeState

/**
 * A menu group frosted over the content [hazeState] records.
 *
 * The frost goes around the group, because the group's own modifier lands inside its container. It
 * keeps one shape, hovered or not, so that the frost, clipped to it, always matches.
 *
 * @param shadowElevation The shadow that lifts the group. None where the menu has a blur halo to do
 *   that, which is the default; a menu without one passes Material's. It is cast from outside the
 *   frost, which is clipped to the group and would cut a shadow of the group's own.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FrostedMenuGroup(
    shapes: MenuGroupShapes,
    hazeState: HazeState,
    shadowElevation: Dp =
        if (DocucraftBlurDefaults.isHaloSupported) 0.dp else MenuDefaults.ShadowElevation,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = shapes.shape
    Box(
        modifier =
            Modifier.shadow(elevation = shadowElevation, shape = shape)
                .frosted(
                    state = hazeState,
                    style =
                        DocucraftBlurDefaults.surfaceStyle(
                            MenuDefaults.groupStandardContainerColor
                        ),
                    shape = shape,
                )
    ) {
        DropdownMenuGroup(
            shapes = MenuGroupShapes(shape = shape, inactiveShape = shape),
            containerColor = Color.Transparent,
            shadowElevation = 0.dp,
            content = content,
        )
    }
}
