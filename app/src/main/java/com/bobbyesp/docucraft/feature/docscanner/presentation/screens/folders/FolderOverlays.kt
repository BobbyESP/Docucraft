/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.folders

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.components.divider.AnimatedWavyDivider
import com.bobbyesp.docucraft.core.presentation.components.divider.defaults.AnimatedWavyDividerDefaults
import com.bobbyesp.docucraft.core.presentation.components.others.GridMenu
import com.bobbyesp.docucraft.core.presentation.components.others.GridMenuItem
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftShapeDefaults
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Folder
import com.bobbyesp.docucraft.feature.docscanner.domain.model.FolderIcon
import com.bobbyesp.docucraft.feature.docscanner.domain.model.LabelColor
import com.bobbyesp.docucraft.feature.docscanner.domain.model.folderIcon
import com.bobbyesp.docucraft.feature.docscanner.domain.model.labelColor
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.organization.FolderBadge
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.organization.FolderIconPicker
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.organization.FolderListItem
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.organization.FormSectionLabel
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.organization.FormSpacer
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.organization.LabelColorPicker
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.organization.NameField
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.organization.OverlayForm
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.organization.folderSummary

/**
 * A folder's name, color and icon. The badge beside the name is the folder as it will look, and
 * changes as the color and the icon are picked.
 */
@Composable
fun FolderEditorForm(
    isNew: Boolean,
    name: String,
    color: LabelColor?,
    icon: FolderIcon,
    nameError: NameError?,
    onNameChange: (String) -> Unit,
    onColorChange: (LabelColor?) -> Unit,
    onIconChange: (FolderIcon) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OverlayForm(
        title = stringResource(if (isNew) R.string.folder_new else R.string.folder_edit),
        icon = if (isNew) Icons.Rounded.CreateNewFolder else Icons.Rounded.Edit,
        onDismiss = onDismiss,
        modifier = modifier,
        confirmText = stringResource(if (isNew) R.string.create else R.string.save),
        onConfirm = onConfirm,
        confirmEnabled = name.isNotBlank(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FolderBadge(icon = icon, color = color, size = 56.dp)
            NameField(
                value = name,
                onValueChange = onNameChange,
                label = stringResource(R.string.name),
                error = nameError,
                takenMessage = stringResource(R.string.folder_name_taken),
                onDone = { if (name.isNotBlank()) onConfirm() },
                modifier = Modifier.weight(1f),
            )
        }

        FormSpacer()
        FormSectionLabel(text = stringResource(R.string.color))
        LabelColorPicker(
            selected = color,
            onSelect = onColorChange,
            modifier = Modifier.padding(top = 8.dp),
        )

        FormSpacer()
        FormSectionLabel(text = stringResource(R.string.icon))
        FolderIconPicker(
            selected = icon,
            onSelect = onIconChange,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * What can be done to a folder, under what the folder is: the same sheet a document has.
 *
 * @param stacked whether there is room to put the header above the actions rather than beside them.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FolderActionsContent(
    folder: Folder,
    onEdit: () -> Unit,
    onSetPinned: (Boolean) -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    stacked: Boolean = true,
) {
    val header: @Composable (Modifier) -> Unit = { headerModifier ->
        Column(
            modifier = headerModifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FolderBadge(icon = folder.folderIcon, color = folder.labelColor, size = 72.dp)
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleLargeEmphasized,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = folderSummary(folder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    val actions: @Composable () -> Unit = {
        GridMenu(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            content = {
                GridMenuItem(
                    icon = Icons.Rounded.Edit,
                    title = R.string.edit,
                    containerColor = { MaterialTheme.colorScheme.primary },
                    onClick = onEdit,
                )
                GridMenuItem(
                    icon = if (folder.isPinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                    title = if (folder.isPinned) R.string.folder_unpin else R.string.folder_pin,
                    maxLines = 2,
                    containerColor = { MaterialTheme.colorScheme.primary },
                    onClick = { onSetPinned(!folder.isPinned) },
                )
                GridMenuItem(
                    icon = Icons.AutoMirrored.Rounded.DriveFileMove,
                    title = R.string.move,
                    containerColor = { MaterialTheme.colorScheme.secondary },
                    onClick = onMove,
                )
                GridMenuItem(
                    icon = Icons.Rounded.DeleteForever,
                    title = R.string.delete,
                    containerColor = { MaterialTheme.colorScheme.error },
                    span = { GridItemSpan(maxLineSpan) },
                    onClick = onDelete,
                )
            },
        )
    }

    if (stacked) {
        Column(modifier = modifier) {
            header(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            AnimatedWavyDivider(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                strokeWidth = 4.dp,
                colors =
                    AnimatedWavyDividerDefaults.colors(
                        color = MaterialTheme.colorScheme.outlineVariant
                    ),
            )
            Box(modifier = Modifier.heightIn(min = 120.dp)) { actions() }
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            header(Modifier.weight(1f).padding(start = 16.dp))
            Box(modifier = Modifier.weight(1f).heightIn(min = 120.dp)) { actions() }
        }
    }
}

/**
 * Says what deleting a folder does before it is done: the folder goes, what it holds stays. A
 * folder that holds something can be deleted with it instead, which is asked for with a switch of
 * its own and never the default: it is what sends documents to the bin.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DeleteFolderForm(
    folder: Folder,
    withContents: Boolean,
    onWithContentsChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OverlayForm(
        title = stringResource(R.string.folder_delete),
        icon = Icons.Rounded.DeleteForever,
        onDismiss = onDismiss,
        modifier = modifier,
        confirmText = stringResource(R.string.delete),
        onConfirm = onConfirm,
        destructive = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.folder_delete_confirmation, folder.name),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Text(
                text =
                    stringResource(
                        if (withContents) R.string.folder_delete_with_contents_note
                        else R.string.folder_delete_note
                    ),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (folder.documentCount > 0 || folder.folderCount > 0) {
                SegmentedListItem(
                    checked = withContents,
                    onCheckedChange = onWithContentsChange,
                    shapes = DocucraftShapeDefaults.segmentedListItemShapes(index = 0, count = 1),
                    modifier = Modifier.padding(top = 8.dp),
                    colors =
                        ListItemDefaults.segmentedColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                    trailingContent = { Switch(checked = withContents, onCheckedChange = null) },
                ) {
                    Text(text = stringResource(R.string.folder_delete_with_contents))
                }
            }
        }
    }
}

/**
 * The folders, walked from inside the overlay to choose where something goes. Where the user is
 * looking is named in the heading, and confirming puts the thing there.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MoveToFolderForm(
    state: MoveToFolderUiState,
    onBrowse: (String?) -> Unit,
    onCreateFolder: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val library = stringResource(R.string.library)
    val location = state.location
    val motionScheme = MaterialTheme.motionScheme

    OverlayForm(
        title = stringResource(R.string.move_to),
        icon = Icons.AutoMirrored.Rounded.DriveFileMove,
        onDismiss = onDismiss,
        modifier = modifier,
        description =
            (listOf(library) + state.path.map { it.name }).joinToString(separator = "  ›  "),
        confirmText = stringResource(R.string.move_here),
        onConfirm = onConfirm,
        confirmEnabled = state.canMoveHere,
    ) {
        // Keyed by where the user is looking, so that going into a folder reads as a change of
        // place rather than as a list whose rows were swapped.
        AnimatedContent(
            targetState = location?.uuid,
            transitionSpec = {
                fadeIn(motionScheme.defaultEffectsSpec()) togetherWith
                    fadeOut(motionScheme.fastEffectsSpec())
            },
            label = "MoveToFolderLocation",
        ) { shownUuid ->
            // The old place fades out showing nothing: what it listed is no longer in the state.
            if (shownUuid != location?.uuid) return@AnimatedContent Box(Modifier.fillMaxWidth())

            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .heightIn(max = FolderListMaxHeight)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                val rows = state.subfolders.size + if (location != null) 1 else 0
                if (location != null) {
                    SegmentedListItem(
                        onClick = { onBrowse(location.parentUuid) },
                        shapes =
                            DocucraftShapeDefaults.segmentedListItemShapes(index = 0, count = rows),
                        colors =
                            ListItemDefaults.segmentedColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                        leadingContent = {
                            Icon(imageVector = Icons.Rounded.ArrowUpward, contentDescription = null)
                        },
                    ) {
                        Text(
                            text =
                                stringResource(
                                    R.string.folder_up_to,
                                    state.path.dropLast(1).lastOrNull()?.name ?: library,
                                )
                        )
                    }
                }
                state.subfolders.forEachIndexed { index, folder ->
                    FolderListItem(
                        folder = folder,
                        onClick = { onBrowse(folder.uuid) },
                        shapes =
                            DocucraftShapeDefaults.segmentedListItemShapes(
                                index = index + if (location != null) 1 else 0,
                                count = rows,
                            ),
                    )
                }
                if (state.subfolders.isEmpty() && !state.isLoading) {
                    Text(
                        text = stringResource(R.string.move_no_folders_here),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                if (state.canCreateFolder) {
                    FilledTonalButton(
                        onClick = onCreateFolder,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CreateNewFolder,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Text(
                            text = stringResource(R.string.folder_new),
                            modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                        )
                    }
                }
            }
        }
    }
}

private val FolderListMaxHeight = 320.dp
