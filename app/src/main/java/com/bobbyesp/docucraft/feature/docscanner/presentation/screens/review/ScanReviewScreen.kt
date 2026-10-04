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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.components.FrostedLargeTopAppBar
import com.bobbyesp.docucraft.core.presentation.components.SectionHeader
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftBlurDefaults
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftShapeDefaults
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
import com.bobbyesp.docucraft.core.presentation.theme.blurHalo
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Folder
import com.bobbyesp.docucraft.feature.docscanner.domain.model.FolderIcon
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Tag
import com.bobbyesp.docucraft.feature.docscanner.domain.model.folderIcon
import com.bobbyesp.docucraft.feature.docscanner.domain.model.labelColor
import com.bobbyesp.docucraft.feature.docscanner.domain.suggestions.DocumentSuggestions
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.organization.FolderBadge
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.organization.TagDot
import com.bobbyesp.docucraft.feature.docscanner.presentation.preview.DocumentPreviewData
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.actions.EditDocumentUiState
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.dialogs.EditDocumentDetailsContent
import com.bobbyesp.docucraft.feature.shared.presentation.DocumentHeroCard
import com.bobbyesp.docucraft.feature.shared.presentation.DocumentHeroFact
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * What the user sees as the scanner closes: a screen of its own, risen over Home, with the page
 * they just scanned and the few things worth saying about it while they still know what it is.
 *
 * Built from what Home is built from, so that it reads as the same app: its app bar, its section
 * headers, its grouped lists and its actions floating at the bottom. Top to bottom: the scan as a
 * card; its title and description, typed in the same fields that edit a document later; what was
 * suggested for it, where anything makes suggestions; and where it goes, the tags it carries and
 * whether its text is recognized. Leaving and saving float at the bottom, where the thumb is.
 * Leaving is always there and costs nothing: the scan is already saved.
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

    // The review's content, recorded for what floats over it: the app bar frosts it, and the
    // actions at the bottom are lifted off it by their halo.
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
            ReviewActions(
                onSkip = onSkip,
                onSave = onSave,
                canSave = fields.canConfirm,
                hazeState = hazeState,
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .hazeSource(hazeState)
                    .verticalScroll(scrollState)
                    .padding(padding)
                    .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // A form as wide as a tablet is hard to read across, so it keeps to a column.
            Column(modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()) {
                val context = LocalContext.current
                DocumentHeroCard(
                    // Named as it is typed, so that the card shows what the document will be.
                    name = fields.title.trim().ifBlank { document.originalName },
                    thumbnail = document.thumbnail,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    // Marked as saved, which it already is.
                    badge = Icons.Rounded.Check,
                ) {
                    // What is not known of the scan is left out, rather than shown as zero.
                    document.pageCount?.let { pages ->
                        DocumentHeroFact(
                            icon = Icons.Rounded.FileCopy,
                            text = pluralStringResource(R.plurals.doc_n_pages, pages, pages),
                        )
                    }
                    document.sizeBytes?.let { size ->
                        DocumentHeroFact(
                            icon = Icons.Rounded.Storage,
                            text = formatShortFileSize(context, size),
                        )
                    }
                }

                SectionHeader(
                    title = stringResource(R.string.scan_review_section_details),
                    modifier = Modifier.padding(top = 8.dp),
                )
                EditDocumentDetailsContent(
                    modifier = Modifier.padding(horizontal = 16.dp),
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
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
                )

                SectionHeader(
                    title = stringResource(R.string.scan_review_section_organize),
                    modifier = Modifier.padding(top = 8.dp),
                )
                Organization(
                    folder = state.folder,
                    tags = state.tags,
                    textRecognition = document.ocrEnabled,
                    onChooseFolder = onChooseFolder,
                    onChooseTags = onChooseTags,
                    onTextRecognitionChange = onTextRecognitionChange,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

/** How wide the review grows on a large window. */
private val ContentMaxWidth = 640.dp

/**
 * Leaving the scan as it came, or keeping what was typed: floating over the review as Home's search
 * and scan float over its list, and lifted off it by the same halo. Above the keyboard when it is
 * up, so that a title is typed and saved without closing it first.
 *
 * @param hazeState Where the content they float over is recorded.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReviewActions(
    onSkip: () -> Unit,
    onSave: () -> Unit,
    canSave: Boolean,
    hazeState: HazeState,
) {
    val height = ButtonDefaults.MediumContainerHeight
    val haloSupported = DocucraftBlurDefaults.isHaloSupported

    Box(
        modifier =
            Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier =
                Modifier.widthIn(max = ContentMaxWidth - 32.dp)
                    .fillMaxWidth()
                    .blurHalo(state = hazeState, shape = CircleShape),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(
                onClick = onSkip,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.heightIn(min = height),
                // The shadow they keep where there is no halo to lift them.
                elevation =
                    if (haloSupported) null
                    else
                        ButtonDefaults.filledTonalButtonElevation(
                            defaultElevation = FallbackElevation,
                            pressedElevation = FallbackElevation,
                        ),
                contentPadding = ButtonDefaults.contentPaddingFor(height),
            ) {
                Text(
                    text = stringResource(R.string.skip),
                    style = ButtonDefaults.textStyleFor(height),
                )
            }
            Button(
                onClick = onSave,
                shapes = ButtonDefaults.shapes(),
                enabled = canSave,
                modifier = Modifier.weight(1f).heightIn(min = height),
                // Material's disabled container is see-through, and this one has the form
                // scrolling beneath it.
                colors =
                    ButtonDefaults.buttonColors(
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                elevation =
                    if (haloSupported) null
                    else
                        ButtonDefaults.buttonElevation(
                            defaultElevation = FallbackElevation,
                            pressedElevation = FallbackElevation,
                        ),
                contentPadding = ButtonDefaults.contentPaddingFor(height, hasStartIcon = true),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.iconSpacingFor(height)))
                Text(
                    text = stringResource(R.string.save),
                    style = ButtonDefaults.textStyleFor(height),
                )
            }
        }
    }
}

private val FallbackElevation = 6.dp

/**
 * Where the scan goes, the tags it carries and whether its text is recognized: one grouped list, as
 * the documents of Home are. The folder is shown by its own badge, in its own color, and the tags
 * by their dots, so that both are recognized here as they are there.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Organization(
    folder: Folder?,
    tags: List<Tag>,
    textRecognition: Boolean,
    onChooseFolder: () -> Unit,
    onChooseTags: () -> Unit,
    onTextRecognitionChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors =
        ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        SegmentedListItem(
            onClick = onChooseFolder,
            shapes = DocucraftShapeDefaults.segmentedListItemShapes(index = 0, count = 3),
            colors = colors,
            verticalAlignment = Alignment.CenterVertically,
            leadingContent = {
                FolderBadge(
                    icon = folder?.folderIcon ?: FolderIcon.Default,
                    color = folder?.labelColor,
                    size = BadgeSize,
                )
            },
            supportingContent = {
                Text(
                    text = folder?.name ?: stringResource(R.string.library),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingContent = { OpensIndicator() },
        ) {
            Text(text = stringResource(R.string.folder))
        }
        SegmentedListItem(
            onClick = onChooseTags,
            shapes = DocucraftShapeDefaults.segmentedListItemShapes(index = 1, count = 3),
            colors = colors,
            verticalAlignment = Alignment.CenterVertically,
            leadingContent = { IconBadge(icon = Icons.AutoMirrored.Rounded.Label) },
            supportingContent = {
                if (tags.isEmpty()) {
                    Text(text = stringResource(R.string.tags_add))
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        for (tag in tags) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                TagDot(color = tag.labelColor, size = 8.dp)
                                Text(
                                    text = tag.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            },
            trailingContent = { OpensIndicator() },
        ) {
            Text(text = stringResource(R.string.tags))
        }
        SegmentedListItem(
            checked = textRecognition,
            onCheckedChange = onTextRecognitionChange,
            shapes = DocucraftShapeDefaults.segmentedListItemShapes(index = 2, count = 3),
            colors = colors,
            verticalAlignment = Alignment.CenterVertically,
            leadingContent = { IconBadge(icon = Icons.Rounded.TextFields) },
            supportingContent = {
                Text(text = stringResource(R.string.scan_review_recognize_text_desc))
            },
            trailingContent = { Switch(checked = textRecognition, onCheckedChange = null) },
        ) {
            Text(text = stringResource(R.string.text_recognition_turn_on))
        }
    }
}

private val BadgeSize = 40.dp

/**
 * An icon on a round badge, the size of the folder's beside it. Round, because the folder's shape
 * is what says it is a folder.
 */
@Composable
private fun IconBadge(icon: ImageVector) {
    Box(
        modifier =
            Modifier.size(BadgeSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(BadgeSize / 2),
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** Says that a row opens something over the review, rather than changing where it is. */
@Composable
private fun OpensIndicator() {
    Icon(
        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
    modifier: Modifier = Modifier,
) {
    if (state == SuggestionsUiState.Hidden) return

    Surface(
        modifier = modifier.fillMaxWidth(),
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
