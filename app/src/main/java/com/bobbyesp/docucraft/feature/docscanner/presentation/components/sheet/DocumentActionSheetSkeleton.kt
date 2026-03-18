package com.bobbyesp.docucraft.feature.docscanner.presentation.components.sheet

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DocumentScanner
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftElevationDefaults
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftShapeDefaults
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme

@Composable
fun DocumentActionSheetSkeleton(
    modifier: Modifier = Modifier,
    elevation: Dp = DocucraftElevationDefaults.Modal,
    header: @Composable () -> Unit = {},
    footer: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Header container
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = elevation,
            shape = DocucraftShapeDefaults.topListItemShape
        ) {
            header()
        }

        // Main content container
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = elevation,
            shape = DocucraftShapeDefaults.bottomListItemShape
        ) {
            content()
        }

        // Footer is kept outside the elevated surfaces
        footer()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DocumentActionSheetSkeleton(
    headingTitle: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    headingDescription: String? = null,
    footer: @Composable () -> Unit = {},
    content: @Composable () -> Unit = {}
) {
    val iconSize: Dp = 28.dp

    // Infinite rotation animation
    val infiniteTransition = rememberInfiniteTransition(label = "SheetIconRotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RotationAngle"
    )

    DocumentActionSheetSkeleton(
        modifier = modifier,
        header = {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                icon?.let {
                    Box(
                        modifier = Modifier.size(iconSize * 2),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .graphicsLayer { rotationZ = rotation }
                                .clip(MaterialShapes.Cookie6Sided.toShape())
                                .background(MaterialTheme.colorScheme.primaryContainer)
                        )

                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(iconSize)
                        )
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = headingTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )

                    headingDescription?.let { desc ->
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        footer = footer,
        content = content
    )
}

@PreviewLightDark
@Composable
private fun DocumentActionSheetSkeletonPreview() {
    DocucraftTheme {
        DocumentActionSheetSkeleton(
            headingTitle = "Action Sheet Title",
            headingDescription = "This is a description for the action sheet.",
            icon = Icons.Rounded.DocumentScanner,
            content = {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Option 1")
                    Text("Option 2")
                    Text("Option 3")
                }
            },
            footer = {
                Text(
                    text = "Footer content goes here.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp)
                )
            }
        )

    }
}