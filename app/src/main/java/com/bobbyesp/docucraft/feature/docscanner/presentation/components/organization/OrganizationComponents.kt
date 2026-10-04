/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.components.organization

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftShapeDefaults
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Folder
import com.bobbyesp.docucraft.feature.docscanner.domain.model.FolderIcon
import com.bobbyesp.docucraft.feature.docscanner.domain.model.LabelColor
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Tag
import com.bobbyesp.docucraft.feature.docscanner.domain.model.folderIcon
import com.bobbyesp.docucraft.feature.docscanner.domain.model.labelColor

/**
 * A folder's icon on a shape of its color. The shape is what tells a folder from a document at a
 * glance: a document is always a page.
 *
 * @param inverted The strong tones, for a badge that sits on the folder's own container color.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FolderBadge(
    icon: FolderIcon,
    color: LabelColor?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    inverted: Boolean = false,
) {
    val tones = color.tones()
    Box(
        modifier =
            modifier
                .size(size)
                .clip(FolderBadgeShape)
                .background(if (inverted) tones.onContainer else tones.container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon.imageVector,
            contentDescription = null,
            modifier = Modifier.size(size / 2),
            tint = if (inverted) tones.container else tones.onContainer,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val FolderBadgeShape: Shape
    @Composable get() = MaterialShapes.Cookie4Sided.toShape()

/** How much a folder holds, in words: "3 documents · 2 folders", or that it is empty. */
@Composable
fun folderSummary(folder: Folder): String {
    val documents =
        folder.documentCount
            .takeIf { it > 0 }
            ?.let { pluralStringResource(R.plurals.n_documents, it, it) }
    val folders =
        folder.folderCount
            .takeIf { it > 0 }
            ?.let { pluralStringResource(R.plurals.n_folders, it, it) }
    return listOfNotNull(documents, folders).joinToString(" · ").ifEmpty {
        stringResource(R.string.folder_empty_short)
    }
}

/**
 * A folder as a card in its own color, for Home's row of pinned folders. Its corners tighten while
 * it is pressed, as every expressive button's do.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FolderCard(
    folder: Folder,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tones = folder.labelColor.tones()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressedFraction by
        animateFloatAsState(
            targetValue = if (pressed) 1f else 0f,
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            label = "FolderCardPressed",
        )
    val shape = RoundedCornerShape(lerp(FolderCardCorner, FolderCardPressedCorner, pressedFraction))

    Column(
        modifier =
            modifier
                .width(152.dp)
                .height(136.dp)
                .clip(shape)
                .background(tones.container)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onLongClickLabel = stringResource(R.string.more_options),
                    onLongClick = onLongClick,
                    onClick = onClick,
                )
                .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        FolderBadge(
            icon = folder.folderIcon,
            color = folder.labelColor,
            size = 44.dp,
            inverted = true,
        )
        Column {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = tones.onContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = folderSummary(folder),
                style = MaterialTheme.typography.labelMedium,
                color = tones.onContainer.copy(alpha = 0.76f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val FolderCardCorner = 28.dp
private val FolderCardPressedCorner = 16.dp

/** A folder as one segment of a grouped list, beside the documents of the folder it is in. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FolderListItem(
    folder: Folder,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shapes: ListItemShapes = DocucraftShapeDefaults.segmentedListItemShapes(index = 0, count = 1),
    onOpenActions: (() -> Unit)? = null,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        modifier = modifier,
        onLongClick = onOpenActions,
        onLongClickLabel = stringResource(R.string.more_options),
        verticalAlignment = Alignment.CenterVertically,
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
        leadingContent = { FolderBadge(icon = folder.folderIcon, color = folder.labelColor) },
        supportingContent = {
            Text(text = folderSummary(folder), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (folder.isPinned) {
                    Icon(
                        imageVector = Icons.Rounded.PushPin,
                        contentDescription = stringResource(R.string.folder_is_pinned),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (onOpenActions != null) {
                    IconButton(onClick = onOpenActions, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.more_options),
                        )
                    }
                }
            }
        },
    ) {
        Text(
            text = folder.name,
            style = MaterialTheme.typography.bodyLargeEmphasized,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The color of a tag, as the dot that goes before its name. */
@Composable
fun TagDot(color: LabelColor?, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    Box(modifier = modifier.size(size).clip(CircleShape).background(color.tones().accent))
}

/** A tag to narrow a list down by, or to put on a document: its dot, then a check once chosen. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TagFilterChip(
    tag: Tag,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = tag.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        shapes = FilterChipDefaults.shapes(),
        modifier = modifier,
        leadingIcon = {
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            } else {
                // In the room an icon would take, so that the name starts where it does once the
                // check replaces the dot.
                Box(
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                    contentAlignment = Alignment.Center,
                ) {
                    TagDot(color = tag.labelColor)
                }
            }
        },
    )
}

/**
 * The palette, as a row of swatches to pick one from: the theme's own color first, which is what no
 * color means. The chosen swatch turns from a circle into a rounded square and shows a check.
 */
@Composable
fun LabelColorPicker(
    selected: LabelColor?,
    onSelect: (LabelColor?) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    Row(
        modifier =
            modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val options: List<LabelColor?> = remember { listOf<LabelColor?>(null) + LabelColor.entries }
        for (option in options) {
            ColorSwatch(
                color = option,
                selected = option == selected,
                onClick = { onSelect(option) },
            )
        }
    }
}

@Composable
private fun ColorSwatch(color: LabelColor?, selected: Boolean, onClick: () -> Unit) {
    val tones = color.tones()
    val name = stringResource(color?.label ?: R.string.color_default)
    val selectedFraction by
        animateFloatAsState(
            targetValue = if (selected) 1f else 0f,
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            label = "ColorSwatchSelected",
        )
    val shape = RoundedCornerShape(lerp(SwatchSize / 2, SwatchSelectedCorner, selectedFraction))

    Box(
        modifier =
            Modifier.size(SwatchSize)
                .clip(shape)
                .background(tones.accent)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            modifier =
                Modifier.size(24.dp).graphicsLayer {
                    alpha = selectedFraction
                    scaleX = 0.6f + 0.4f * selectedFraction
                    scaleY = scaleX
                },
            tint = tones.container,
        )
    }
}

private val SwatchSize = 48.dp
private val SwatchSelectedCorner = 14.dp

/** The icons a folder can have, each a toggle that takes its round shape square when chosen. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FolderIconPicker(
    selected: FolderIcon,
    onSelect: (FolderIcon) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (icon in FolderIcon.entries) {
            FilledTonalIconToggleButton(
                checked = icon == selected,
                onCheckedChange = { onSelect(icon) },
                shapes = IconButtonDefaults.toggleableShapes(),
            ) {
                Icon(
                    imageVector = icon.imageVector,
                    contentDescription = stringResource(icon.label),
                )
            }
        }
    }
}

/** A label over a group of controls in a form, such as the color and the icon of a folder. */
@Composable
fun FormSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Vertical room between the groups of a form. */
@Composable
fun FormSpacer() {
    Spacer(modifier = Modifier.height(16.dp))
}
