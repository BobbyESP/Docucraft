/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * An icon button whose [label] is both its tooltip and what TalkBack reads. Material asks every
 * icon-only button for a tooltip; tying the two to one string keeps them from drifting apart.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TooltipIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tooltipPosition: TooltipAnchorPosition = TooltipAnchorPosition.Below,
) {
    WithTooltip(label = label, position = tooltipPosition) {
        IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(imageVector = icon, contentDescription = label)
        }
    }
}

/** Wraps [content] in a plain tooltip reading [label]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WithTooltip(
    label: String,
    position: TooltipAnchorPosition = TooltipAnchorPosition.Below,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(position),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        content = content,
    )
}
