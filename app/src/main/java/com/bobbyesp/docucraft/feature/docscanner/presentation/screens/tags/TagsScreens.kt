/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.tags

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.NewLabel
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.components.FrostedLargeTopAppBar
import com.bobbyesp.docucraft.core.presentation.components.ScreenPlaceholderCard
import com.bobbyesp.docucraft.core.presentation.components.SectionHeader
import com.bobbyesp.docucraft.core.presentation.components.overlay.OverlayForm
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftBlurDefaults
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftShapeDefaults
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
import com.bobbyesp.docucraft.core.presentation.theme.blurHalo
import com.bobbyesp.docucraft.core.util.animateItemWith
import com.bobbyesp.docucraft.core.util.contentRevealTransform
import com.bobbyesp.docucraft.feature.docscanner.domain.model.LabelColor
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Tag
import com.bobbyesp.docucraft.feature.docscanner.domain.model.labelColor
import com.bobbyesp.docucraft.feature.docscanner.domain.model.normalizedNameOf
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.list.rememberFabExpansionState
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.organization.FormSectionLabel
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.organization.FormSpacer
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.organization.LabelColorPicker
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.organization.NameField
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.organization.TagDot
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.organization.TagFilterChip
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.folders.NameError
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

// ---------------- The tags of one document ----------------

/**
 * The tags of a document: every tag as a chip, the document's own checked. Typing finds a tag among
 * many, and names a new one when none is called that. Nothing to confirm: each tap is the change.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DocumentTagsForm(
    state: DocumentTagsUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    onToggle: (String) -> Unit,
    onAddByName: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val wanted = normalizedNameOf(query)
    val shown =
        remember(state.tags, wanted) {
            if (wanted.isEmpty()) state.tags
            else state.tags.filter { wanted in normalizedNameOf(it.name) }
        }
    val canCreate = wanted.isNotEmpty() && state.tags.none { normalizedNameOf(it.name) == wanted }

    OverlayForm(
        title = stringResource(R.string.tags),
        icon = Icons.AutoMirrored.Rounded.Label,
        onDismiss = onDismiss,
        modifier = modifier,
        description = stringResource(R.string.document_tags_desc),
        dismissText = stringResource(R.string.done),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { if (it.length <= TagNameMaxLength) onQueryChange(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(R.string.tag_find_or_create)) },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
            keyboardActions = KeyboardActions(onDone = { if (wanted.isNotEmpty()) onAddByName() }),
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (canCreate) {
                AssistChip(
                    onClick = onAddByName,
                    label = {
                        Text(text = stringResource(R.string.tag_create_named, query.trim()))
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(AssistChipDefaults.IconSize),
                        )
                    },
                )
            }
            for (tag in shown) {
                TagFilterChip(
                    tag = tag,
                    selected = tag.uuid in state.assigned,
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onToggle(tag.uuid)
                    },
                )
            }
        }

        if (state.tags.isEmpty() && !state.isLoading && !canCreate) {
            Text(
                text = stringResource(R.string.tags_none_desc),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private const val TagNameMaxLength = 40

// ---------------- Every tag ----------------

private enum class TagsPage {
    Loading,
    Empty,
    Tags,
}

/**
 * Every tag, in two groups: those with a section of their own in Home, in Home's order and with the
 * arrows that change it, and the rest. The switch on each moves it from one group to the other.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ManageTagsScreen(
    uiState: TagsUiState,
    onAction: (TagsIntent) -> Unit,
    onBack: () -> Unit,
    onEditTag: (String) -> Unit,
    onCreateTag: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val motionScheme = MaterialTheme.motionScheme
    val layoutDirection = LocalLayoutDirection.current
    val hazeState = rememberHazeState()

    val page =
        when {
            uiState.isLoading -> TagsPage.Loading
            uiState.isEmpty -> TagsPage.Empty
            else -> TagsPage.Tags
        }
    val fabExpansion = rememberFabExpansionState(listState)
    val haloStrength by
        animateFloatAsState(
            targetValue = if (page == TagsPage.Tags) 1f else 0f,
            animationSpec = motionScheme.defaultEffectsSpec(),
            label = "NewTagHalo",
        )

    Scaffold(
        modifier =
            modifier
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .nestedScroll(fabExpansion.nestedScrollConnection),
        topBar = {
            FrostedLargeTopAppBar(
                title = stringResource(R.string.tags),
                isContentScrolled = listState.canScrollBackward,
                scrollBehavior = scrollBehavior,
                hazeState = hazeState,
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            SmallExtendedFloatingActionButton(
                text = { Text(text = stringResource(R.string.tag_new)) },
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.NewLabel,
                        contentDescription = null,
                    )
                },
                onClick = onCreateTag,
                expanded = fabExpansion.isExpanded,
                modifier =
                    Modifier.blurHalo(
                            state = hazeState,
                            shape = FloatingActionButtonDefaults.smallExtendedFabShape,
                            strength = haloStrength,
                        )
                        // The empty state carries its own button.
                        .animateFloatingActionButton(
                            visible = page == TagsPage.Tags,
                            alignment = Alignment.BottomEnd,
                        ),
                elevation =
                    if (DocucraftBlurDefaults.isHaloSupported) {
                        FloatingActionButtonDefaults.elevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp,
                            focusedElevation = 0.dp,
                            hoveredElevation = 0.dp,
                        )
                    } else {
                        FloatingActionButtonDefaults.elevation()
                    },
            )
        },
    ) { padding ->
        AnimatedContent(
            modifier = Modifier.fillMaxSize().hazeSource(hazeState),
            targetState = page,
            transitionSpec = { motionScheme.contentRevealTransform() },
            label = "TagsPage",
        ) { targetPage ->
            val centered = Modifier.fillMaxSize().padding(padding)
            when (targetPage) {
                TagsPage.Loading ->
                    Box(modifier = centered, contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }

                TagsPage.Empty ->
                    Box(modifier = centered, contentAlignment = Alignment.Center) {
                        ScreenPlaceholderCard(
                            modifier = Modifier.padding(24.dp),
                            title = stringResource(R.string.tags_none_title),
                            description = stringResource(R.string.tags_none_desc),
                            icon = Icons.AutoMirrored.Rounded.Label,
                            actionText = stringResource(R.string.tag_new),
                            iconAction = Icons.Rounded.NewLabel,
                            onAction = onCreateTag,
                        )
                    }

                TagsPage.Tags ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding =
                            PaddingValues(
                                start = padding.calculateStartPadding(layoutDirection),
                                top = padding.calculateTopPadding() + 8.dp,
                                end = padding.calculateEndPadding(layoutDirection),
                                bottom = padding.calculateBottomPadding() + 88.dp,
                            ),
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                    ) {
                        val sections = uiState.homeSections
                        if (sections.isNotEmpty()) {
                            item(key = "home-header", contentType = "section-header") {
                                SectionHeader(
                                    title = stringResource(R.string.tags_in_home),
                                    modifier = animateItemWith(motionScheme),
                                )
                            }
                            itemsIndexed(
                                items = sections,
                                key = { _, tag -> tag.uuid },
                                contentType = { _, _ -> "tag" },
                            ) { index, tag ->
                                TagListItem(
                                    tag = tag,
                                    shownInHome = true,
                                    shapes =
                                        DocucraftShapeDefaults.segmentedListItemShapes(
                                            index = index,
                                            count = sections.size,
                                        ),
                                    onClick = { onEditTag(tag.uuid) },
                                    onShownInHomeChange = {
                                        onAction(TagsIntent.SetShownInHome(tag.uuid, it))
                                    },
                                    onMoveUp = {
                                            onAction(TagsIntent.MoveSection(tag.uuid, by = -1))
                                        }
                                            .takeIf { index > 0 },
                                    onMoveDown = {
                                            onAction(TagsIntent.MoveSection(tag.uuid, by = 1))
                                        }
                                            .takeIf { index < sections.lastIndex },
                                    modifier = animateItemWith(motionScheme),
                                )
                            }
                        }

                        val others = uiState.otherTags
                        if (others.isNotEmpty()) {
                            item(key = "others-header", contentType = "section-header") {
                                SectionHeader(
                                    title =
                                        stringResource(
                                            if (sections.isEmpty()) R.string.tags_all
                                            else R.string.tags_others
                                        ),
                                    modifier =
                                        Modifier.padding(
                                                top = if (sections.isEmpty()) 0.dp else 16.dp
                                            )
                                            .then(animateItemWith(motionScheme)),
                                )
                            }
                            itemsIndexed(
                                items = others,
                                key = { _, tag -> tag.uuid },
                                contentType = { _, _ -> "tag" },
                            ) { index, tag ->
                                TagListItem(
                                    tag = tag,
                                    shownInHome = false,
                                    shapes =
                                        DocucraftShapeDefaults.segmentedListItemShapes(
                                            index = index,
                                            count = others.size,
                                        ),
                                    onClick = { onEditTag(tag.uuid) },
                                    onShownInHomeChange = {
                                        onAction(TagsIntent.SetShownInHome(tag.uuid, it))
                                    },
                                    onMoveUp = null,
                                    onMoveDown = null,
                                    modifier = animateItemWith(motionScheme),
                                )
                            }
                        }
                    }
            }
        }
    }
}

/**
 * @param onMoveUp Moves the tag's section one place up in Home. `null` where it cannot go: for the
 *   first one, and for a tag without a section. The same goes for [onMoveDown].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TagListItem(
    tag: Tag,
    shownInHome: Boolean,
    shapes: ListItemShapes,
    onClick: () -> Unit,
    onShownInHomeChange: (Boolean) -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val showInHome = stringResource(R.string.tag_show_in_home)

    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
        leadingContent = { TagDot(color = tag.labelColor, size = 20.dp) },
        supportingContent =
            if (shownInHome) {
                { Text(text = stringResource(R.string.tag_has_home_section)) }
            } else {
                null
            },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (shownInHome) {
                    IconButton(
                        onClick = { onMoveUp?.invoke() },
                        enabled = onMoveUp != null,
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowUpward,
                            contentDescription = stringResource(R.string.move_up),
                        )
                    }
                    IconButton(
                        onClick = { onMoveDown?.invoke() },
                        enabled = onMoveDown != null,
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowDownward,
                            contentDescription = stringResource(R.string.move_down),
                        )
                    }
                }
                Switch(
                    checked = shownInHome,
                    onCheckedChange = onShownInHomeChange,
                    modifier = Modifier.semantics { contentDescription = showInHome },
                )
            }
        },
    ) {
        Text(
            text = tag.name,
            style = MaterialTheme.typography.bodyLargeEmphasized,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------- One tag ----------------

/** A tag's name and color, and for one that exists the way to delete it. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TagEditorForm(
    isNew: Boolean,
    name: String,
    color: LabelColor?,
    nameError: NameError?,
    onNameChange: (String) -> Unit,
    onColorChange: (LabelColor?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OverlayForm(
        title = stringResource(if (isNew) R.string.tag_new else R.string.tag_edit),
        icon = if (isNew) Icons.Rounded.NewLabel else Icons.Rounded.Edit,
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
            TagDot(color = color, size = 28.dp)
            NameField(
                value = name,
                onValueChange = onNameChange,
                label = stringResource(R.string.name),
                error = nameError,
                takenMessage = stringResource(R.string.tag_name_taken),
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

        if (!isNew) {
            TextButton(
                onClick = onDelete,
                shapes = ButtonDefaults.shapes(),
                colors =
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteForever,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Text(
                    text = stringResource(R.string.tag_delete),
                    modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                )
            }
        }
    }
}

@Composable
fun DeleteTagForm(
    tag: Tag,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OverlayForm(
        title = stringResource(R.string.tag_delete),
        icon = Icons.Rounded.DeleteForever,
        onDismiss = onDismiss,
        modifier = modifier,
        confirmText = stringResource(R.string.delete),
        onConfirm = onConfirm,
        destructive = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.tag_delete_confirmation, tag.name),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.tag_delete_note),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ManageTagsScreenPreview() {
    DocucraftTheme {
        ManageTagsScreen(
            uiState =
                TagsUiState(
                    homeSections =
                        listOf(
                            Tag("a", "Invoices", color = "amber", homePosition = 0),
                            Tag("b", "Health", color = "teal", homePosition = 1),
                        ),
                    otherTags = listOf(Tag("c", "Warranty", color = null, homePosition = null)),
                    isLoading = false,
                ),
            onAction = {},
            onBack = {},
            onEditTag = {},
            onCreateTag = {},
        )
    }
}
