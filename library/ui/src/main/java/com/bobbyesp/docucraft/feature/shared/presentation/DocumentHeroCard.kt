/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.shared.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.core.presentation.components.image.AsyncImage
import com.skydoves.landscapist.ImageOptions

/**
 * A document as a card in the theme's own color, as a folder without one is on Home: its first page
 * at one side, and beside it its name and what is known of it. Wide rather than tall, so that what
 * is under it is on screen without scrolling; the page is there to be recognized, not read.
 *
 * The page lands a little askew and its badge springs in, once: not again after a rotation. Shared
 * by the screens that present one document, the review of a scan and the details of an open one, so
 * that a document is introduced the same way in both.
 *
 * @param thumbnail What the image loader draws the first page from, or `null` for a document that
 *   has no preview, which shows a placeholder.
 * @param description What the user wrote about it, under its name.
 * @param badge What marks the page, such as that it was just saved, or where it came from.
 * @param facts What is known of it, each a [DocumentHeroFact].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DocumentHeroCard(
    name: String,
    thumbnail: Any?,
    modifier: Modifier = Modifier,
    description: String? = null,
    badge: ImageVector? = null,
    facts: @Composable FlowRowScope.() -> Unit = {},
) {
    val isPreview = LocalInspectionMode.current
    var hasLanded by rememberSaveable { mutableStateOf(isPreview) }
    LaunchedEffect(Unit) { hasLanded = true }
    val landing by
        animateFloatAsState(
            targetValue = if (hasLanded) 1f else 0f,
            animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
            label = "DocumentHeroLanding",
        )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Box {
                Box(
                    modifier =
                        Modifier.width(PageWidth)
                            .aspectRatio(Measurements.A4_RATIO)
                            .graphicsLayer { rotationZ = PageTilt * landing }
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    val placeholder =
                        @Composable {
                            Icon(
                                imageVector = Icons.Rounded.Description,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    if (isPreview || thumbnail == null) {
                        placeholder()
                    } else {
                        AsyncImage(
                            modifier = Modifier.fillMaxSize(),
                            imageModel = thumbnail,
                            shape = RectangleShape,
                            // A page's heading is at its top, and is what identifies it.
                            imageOptions =
                                ImageOptions(
                                    alignment = Alignment.TopCenter,
                                    contentDescription = null,
                                ),
                            failure = { placeholder() },
                        )
                    }
                }
                if (badge != null) {
                    Box(
                        modifier =
                            Modifier.align(Alignment.BottomEnd)
                                .offset(x = 10.dp, y = 10.dp)
                                .size(36.dp)
                                .graphicsLayer {
                                    scaleX = landing
                                    scaleY = landing
                                }
                                .clip(MaterialShapes.Cookie9Sided.toShape())
                                .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = badge,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!description.isNullOrBlank()) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = facts,
                )
            }
        }
    }
}

private val PageWidth = 96.dp

/** How far the page leans once it has landed, in degrees. */
private const val PageTilt = -4f

/** One thing known of a document, as a pill on its [DocumentHeroCard]. */
@Composable
fun DocumentHeroFact(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .padding(start = 8.dp, top = 6.dp, end = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}
