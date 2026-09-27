/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftShapeDefaults
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme

/**
 * What a screen shows instead of its content: nothing there yet, nothing matching, or an error.
 *
 * A tonal container and no border or shadow: it sits on the page like any other grouped surface,
 * and the slowly turning shape behind the icon is what draws the eye, not chrome around it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ScreenPlaceholderCard(
    title: String,
    description: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    actionText: String? = null,
    iconAction: ImageVector = Icons.Rounded.CameraAlt,
    onAction: (() -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme

    // Container and content roles in their pairs, so the icon stays legible under dynamic colour
    // and the higher contrast levels.
    val shapeColor = if (isError) colorScheme.errorContainer else colorScheme.primaryContainer
    val onShapeColor = if (isError) colorScheme.onErrorContainer else colorScheme.onPrimaryContainer

    val infiniteTransition = rememberInfiniteTransition(label = "PlaceholderRotation")
    val rotation by
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(if (isError) 20_000 else 12_000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "RotationAngle",
        )

    Surface(
        modifier = modifier.widthIn(max = 480.dp).fillMaxWidth(),
        shape = DocucraftShapeDefaults.cardShape,
        color = colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier =
                        Modifier.matchParentSize()
                            .graphicsLayer { rotationZ = rotation }
                            .clip(MaterialShapes.Cookie9Sided.toShape())
                            .background(shapeColor)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = onShapeColor,
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = colorScheme.onSurfaceVariant,
                )
            }

            if (actionText != null && onAction != null) {
                val buttonHeight = ButtonDefaults.MediumContainerHeight
                Button(
                    onClick = onAction,
                    modifier = Modifier.padding(top = 8.dp).heightIn(min = buttonHeight),
                    shapes = ButtonDefaults.shapes(),
                    colors =
                        if (isError) {
                            ButtonDefaults.buttonColors(
                                containerColor = colorScheme.error,
                                contentColor = colorScheme.onError,
                            )
                        } else {
                            ButtonDefaults.buttonColors()
                        },
                    contentPadding =
                        ButtonDefaults.contentPaddingFor(buttonHeight, hasStartIcon = true),
                ) {
                    Icon(
                        imageVector = iconAction,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.iconSizeFor(buttonHeight)),
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.iconSpacingFor(buttonHeight)))
                    Text(text = actionText, style = ButtonDefaults.textStyleFor(buttonHeight))
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun ScreenPlaceholderCardPreview() {
    DocucraftTheme {
        ScreenPlaceholderCard(
            title = "No scanned documents",
            description = "Scan your first document to see it here.",
            actionText = "Scan new document",
            onAction = {},
            icon = Icons.Rounded.FileCopy,
        )
    }
}

@PreviewLightDark
@Composable
private fun ScreenPlaceholderCardErrorPreview() {
    DocucraftTheme {
        ScreenPlaceholderCard(
            title = "Unknown error",
            description = "Couldn't reach local storage",
            icon = Icons.Rounded.FileCopy,
            isError = true,
        )
    }
}
