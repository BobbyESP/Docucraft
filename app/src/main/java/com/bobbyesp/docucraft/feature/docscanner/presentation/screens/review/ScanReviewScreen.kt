/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.review

import android.text.format.Formatter.formatShortFileSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.components.image.AsyncImage
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftShapeDefaults
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Tag
import com.bobbyesp.docucraft.feature.docscanner.domain.model.labelColor
import com.bobbyesp.docucraft.feature.docscanner.domain.suggestions.DocumentSuggestions
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.list.FrostedLargeTopAppBar
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.organization.TagDot
import com.bobbyesp.docucraft.feature.docscanner.presentation.preview.DocumentPreviewData
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.actions.EditDocumentUiState
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.dialogs.EditDocumentDetailsContent
import com.bobbyesp.docucraft.feature.shared.presentation.Measurements
import com.skydoves.landscapist.ImageOptions
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * What the user sees as the scanner closes: a screen of its own, risen over Home, with the page
 * they just scanned and the few things worth saying about it while they still know what it is.
 *
 * Top to bottom: the scan as a card, and the tags it carries; its title and description, typed in
 * the same fields that edit a document later; what was suggested for it, where anything makes
 * suggestions; and where it goes and whether its text is recognized. Leaving and saving are pinned
 * at the bottom, where the thumb is. Leaving is always there and costs nothing: the scan is already
 * saved.
 *
 * @param fields What has been typed, with what is wrong with it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ScanReviewScreen(
    state: ScanReviewUiState,
    document: Document.Managed,
    fields: EditDocumentUiState,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onChooseFolder: () -> Unit,
    onChooseTags: () -> Unit,
    onTextRecognitionChange: (Boolean) -> Unit,
    onAddSuggestedTag: (String) -> Unit,
    onMoveToSuggestedFolder: () -> Unit,
    onOpenSettings: () -> Unit,
    onSkip: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = rememberHazeState()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            FrostedLargeTopAppBar(
                title = stringResource(R.string.scan_review_title),
                subtitle = stringResource(R.string.scan_review_desc),
                isContentScrolled = scrollState.canScrollBackward,
                scrollBehavior = scrollBehavior,
                hazeState = hazeState,
                navigationIcon = {
                    IconButton(onClick = onSkip, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.skip),
                        )
                    }
                },
            )
        },
        bottomBar = {
            ReviewActions(onSkip = onSkip, onSave = onSave, canSave = fields.canConfirm)
        },
    ) { padding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .hazeSource(hazeState)
                    .verticalScroll(scrollState)
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScanPreview(document = document)

            AssignedTags(tags = state.tags, onChooseTags = onChooseTags)

            EditDocumentDetailsContent(
                state = fields,
                onTitleChange = onTitleChange,
                onDescriptionChange = onDescriptionChange,
            )

            Suggestions(
                state = state.suggestions,
                onUseTitle = { onTitleChange(it.take(EditDocumentUiState.TITLE_MAX_LENGTH)) },
                onUseDescription = {
                    onDescriptionChange(it.take(EditDocumentUiState.DESCRIPTION_MAX_LENGTH))
                },
                onAddTag = onAddSuggestedTag,
                onMoveToFolder = onMoveToSuggestedFolder,
                onOpenSettings = onOpenSettings,
                currentTags = state.tags,
            )

            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                SegmentedListItem(
                    onClick = onChooseFolder,
                    shapes = DocucraftShapeDefaults.segmentedListItemShapes(index = 0, count = 2),
                    colors =
                        ListItemDefaults.segmentedColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                    leadingContent = {
                        Icon(imageVector = Icons.Rounded.Folder, contentDescription = null)
                    },
                    supportingContent = {
                        Text(
                            text = state.folder?.name ?: stringResource(R.string.library),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                ) {
                    Text(text = stringResource(R.string.folder))
                }
                SegmentedListItem(
                    checked = document.ocrEnabled,
                    onCheckedChange = onTextRecognitionChange,
                    shapes = DocucraftShapeDefaults.segmentedListItemShapes(index = 1, count = 2),
                    colors =
                        ListItemDefaults.segmentedColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                    leadingContent = {
                        Icon(imageVector = Icons.Rounded.TextFields, contentDescription = null)
                    },
                    supportingContent = {
                        Text(text = stringResource(R.string.scan_review_recognize_text_desc))
                    },
                    trailingContent = {
                        Switch(checked = document.ocrEnabled, onCheckedChange = null)
                    },
                ) {
                    Text(text = stringResource(R.string.text_recognition_turn_on))
                }
            }
        }
    }
}

/**
 * Leaving the scan as it came, or keeping what was typed. Above the keyboard when it is up, so that
 * a title is typed and saved without closing it first.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReviewActions(onSkip: () -> Unit, onSave: () -> Unit, canSave: Boolean) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onSkip,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.weight(1f).heightIn(min = ActionHeight),
            ) {
                Text(text = stringResource(R.string.skip))
            }
            Button(
                onClick = onSave,
                shapes = ButtonDefaults.shapes(),
                enabled = canSave,
                modifier = Modifier.weight(1f).heightIn(min = ActionHeight),
            ) {
                Text(text = stringResource(R.string.save))
            }
        }
    }
}

private val ActionHeight = 48.dp

/**
 * The scan as a card: its first page at one side, and beside it what is known of it. Wide rather
 * than tall, so that the fields under it are on screen without scrolling; the page is there to be
 * recognized, not read.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScanPreview(document: Document.Managed, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxWidth()) {
        SectionLabel(text = stringResource(R.string.preview))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = DocucraftShapeDefaults.cardShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier =
                        Modifier.width(PreviewWidth)
                            .aspectRatio(Measurements.A4_RATIO)
                            .clip(MaterialTheme.shapes.large)
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
                    if (LocalInspectionMode.current) {
                        placeholder()
                    } else {
                        AsyncImage(
                            modifier = Modifier.fillMaxSize(),
                            imageModel = document.thumbnail,
                            shape = RectangleShape,
                            imageOptions =
                                ImageOptions(
                                    alignment = Alignment.TopCenter,
                                    contentDescription = null,
                                ),
                            failure = { placeholder() },
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = document.originalName,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // What is not known of the scan is left out, rather than shown as zero.
                    document.pageCount?.let { pages ->
                        Fact(
                            icon = Icons.Rounded.FileCopy,
                            text = pluralStringResource(R.plurals.doc_n_pages, pages, pages),
                        )
                    }
                    document.sizeBytes?.let { size ->
                        Fact(
                            icon = Icons.Rounded.Storage,
                            text = formatShortFileSize(context, size),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Fact(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The name of a part of the review, as the sections of a list are named. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp),
        style =
            MaterialTheme.typography.labelLargeEmphasized.copy(
                letterSpacing = 1.25.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            ),
    )
}

private val PreviewWidth = 88.dp

/**
 * The tags the scan carries, under it, and the way to change them at the end of the row. With none,
 * the row is only that way in, named for what it does then.
 */
@Composable
private fun AssignedTags(tags: List<Tag>, onChooseTags: () -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (tag in tags) {
            AssistChip(
                onClick = onChooseTags,
                label = { Text(text = tag.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = {
                    Box(
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                        contentAlignment = Alignment.Center,
                    ) {
                        TagDot(color = tag.labelColor)
                    }
                },
            )
        }
        AssistChip(
            onClick = onChooseTags,
            label = {
                Text(
                    text =
                        stringResource(
                            if (tags.isEmpty()) R.string.tags_add else R.string.tags_edit_short
                        )
                )
            },
            leadingIcon = {
                Icon(
                    imageVector =
                        if (tags.isEmpty()) Icons.Rounded.Add else Icons.AutoMirrored.Rounded.Label,
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            },
        )
    }
}

/**
 * What was proposed for the document, each part taken with a tap, or why nothing was. Nothing at
 * all where nothing makes suggestions.
 *
 * @param currentTags The tags the document has, so that a suggested one it already carries is not
 *   offered again.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Suggestions(
    state: SuggestionsUiState,
    onUseTitle: (String) -> Unit,
    onUseDescription: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onMoveToFolder: () -> Unit,
    onOpenSettings: () -> Unit,
    currentTags: List<Tag>,
) {
    if (state == SuggestionsUiState.Hidden) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = DocucraftShapeDefaults.cardShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state == SuggestionsUiState.Working) {
                    LoadingIndicator(
                        modifier = Modifier.size(24.dp),
                        color = LocalContentColor.current,
                    )
                } else {
                    Icon(imageVector = Icons.Rounded.AutoAwesome, contentDescription = null)
                }
                Text(
                    text = stringResource(R.string.suggestions),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
            }

            when (state) {
                SuggestionsUiState.Hidden -> Unit
                SuggestionsUiState.Working ->
                    Text(text = stringResource(R.string.suggestions_working))
                SuggestionsUiState.Nothing -> Text(text = stringResource(R.string.suggestions_none))
                SuggestionsUiState.Failed ->
                    Text(text = stringResource(R.string.suggestions_failed))
                SuggestionsUiState.TextRecognitionOff -> {
                    Text(text = stringResource(R.string.suggestions_text_recognition_off))
                    TextButton(
                        onClick = onOpenSettings,
                        shapes = ButtonDefaults.shapes(),
                        colors =
                            ButtonDefaults.textButtonColors(
                                contentColor = LocalContentColor.current
                            ),
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(text = stringResource(R.string.settings))
                    }
                }
                is SuggestionsUiState.Ready -> {
                    val suggestions = state.suggestions
                    suggestions.title?.let { suggested ->
                        SuggestedText(
                            label = stringResource(R.string.title),
                            text = suggested,
                            onUse = { onUseTitle(suggested) },
                        )
                    }
                    suggestions.description?.let { suggested ->
                        SuggestedText(
                            label = stringResource(R.string.description),
                            text = suggested,
                            onUse = { onUseDescription(suggested) },
                        )
                    }
                    val carried = currentTags.map { it.name.lowercase() }
                    val newTags = suggestions.tagNames.filter { it.lowercase() !in carried }
                    if (state.folder != null || newTags.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.folder?.let { folder ->
                                SuggestionChip(
                                    icon = Icons.Rounded.Folder,
                                    text = stringResource(R.string.suggestion_move_to, folder.name),
                                    onClick = onMoveToFolder,
                                )
                            }
                            for (name in newTags) {
                                SuggestionChip(
                                    icon = Icons.Rounded.Add,
                                    text = name,
                                    onClick = { onAddTag(name) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SuggestedText(label: String, text: String, onUse: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(text = text, style = MaterialTheme.typography.bodyLarge)
        }
        TextButton(
            onClick = onUse,
            shapes = ButtonDefaults.shapes(),
            colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current),
        ) {
            Text(text = stringResource(R.string.suggestion_use))
        }
    }
}

@Composable
private fun SuggestionChip(icon: ImageVector, text: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
    )
}

@PreviewLightDark
@Composable
private fun ScanReviewScreenPreview() {
    DocucraftTheme {
        ScanReviewScreen(
            state =
                ScanReviewUiState(
                    tags =
                        listOf(
                            Tag("a", "Receipts", color = "amber", homePosition = null),
                            Tag("b", "Home", color = "teal", homePosition = null),
                        ),
                    suggestions =
                        SuggestionsUiState.Ready(
                            suggestions =
                                DocumentSuggestions(
                                    title = "Rent receipt, October",
                                    tagNames = listOf("Rent"),
                                ),
                            folder = null,
                        ),
                ),
            document = DocumentPreviewData.documents.first(),
            fields = EditDocumentUiState.of(title = "Rent", description = ""),
            onTitleChange = {},
            onDescriptionChange = {},
            onChooseFolder = {},
            onChooseTags = {},
            onTextRecognitionChange = {},
            onAddSuggestedTag = {},
            onMoveToSuggestedFolder = {},
            onOpenSettings = {},
            onSkip = {},
            onSave = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun ScanReviewScreenTextRecognitionOffPreview() {
    DocucraftTheme {
        ScanReviewScreen(
            state = ScanReviewUiState(suggestions = SuggestionsUiState.TextRecognitionOff),
            document = DocumentPreviewData.documents.first(),
            fields = EditDocumentUiState.of(title = "", description = ""),
            onTitleChange = {},
            onDescriptionChange = {},
            onChooseFolder = {},
            onChooseTags = {},
            onTextRecognitionChange = {},
            onAddSuggestedTag = {},
            onMoveToSuggestedFolder = {},
            onOpenSettings = {},
            onSkip = {},
            onSave = {},
        )
    }
}
