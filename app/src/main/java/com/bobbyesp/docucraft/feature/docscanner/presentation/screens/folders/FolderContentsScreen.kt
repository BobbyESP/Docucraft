/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.folders

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.components.ScreenPlaceholderCard
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftBlurDefaults
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftShapeDefaults
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
import com.bobbyesp.docucraft.core.presentation.theme.blurHalo
import com.bobbyesp.docucraft.core.util.animateItemWith
import com.bobbyesp.docucraft.core.util.contentRevealTransform
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Folder
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.card.ScannedDocumentListItem
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.list.FrostedLargeTopAppBar
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.list.SectionHeader
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.list.SortMenu
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.organization.FolderListItem
import com.bobbyesp.docucraft.feature.docscanner.presentation.preview.DocumentPreviewData
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.collectLatest

/** Where a folder's screen sends the user. Navigation, so none of it is an intent. */
data class FolderContentsNavigation(
    val onBack: () -> Unit = {},
    val onOpenDocument: (String) -> Unit = {},
    val onOpenDocumentActions: (String) -> Unit = {},
    val onOpenFolder: (String) -> Unit = {},
    val onOpenFolderActions: (String) -> Unit = {},
    val onCreateFolder: () -> Unit = {},
    /** The folder was deleted while its screen was open. */
    val onFolderGone: () -> Unit = {},
)

@Composable
fun FolderContentsScreen(
    viewModel: FolderContentsViewModel,
    navigation: FolderContentsNavigation,
    modifier: Modifier = Modifier,
    selectedDocumentId: String? = null,
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val onFolderGone by rememberUpdatedState(navigation.onFolderGone)

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                FolderContentsEffect.Close -> onFolderGone()
            }
        }
    }

    FolderContentsContent(
        uiState = uiState,
        onAction = viewModel::onSendIntent,
        navigation = navigation,
        modifier = modifier,
        selectedDocumentId = selectedDocumentId,
    )
}

private enum class FolderPage {
    Loading,
    Empty,
    Contents,
}

/**
 * Room for the last item to scroll clear of the button floating over the list: its height (56dp),
 * the scaffold's margin under it, and the list's own gap.
 */
private val FabClearance = 88.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FolderContentsContent(
    uiState: FolderContentsUiState,
    onAction: (FolderContentsIntent) -> Unit,
    navigation: FolderContentsNavigation,
    modifier: Modifier = Modifier,
    selectedDocumentId: String? = null,
) {
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val motionScheme = MaterialTheme.motionScheme
    val layoutDirection = LocalLayoutDirection.current
    val hazeState = rememberHazeState()
    val folder = uiState.folder

    val page =
        when {
            uiState.isLoading -> FolderPage.Loading
            uiState.isEmpty -> FolderPage.Empty
            else -> FolderPage.Contents
        }

    val isFabExpanded by remember {
        derivedStateOf { !listState.lastScrolledForward || !listState.canScrollBackward }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            FrostedLargeTopAppBar(
                title = folder?.name ?: stringResource(R.string.folders),
                // Where the folder is, which its name alone does not say two levels down.
                subtitle =
                    uiState.path
                        .dropLast(1)
                        .takeIf { it.isNotEmpty() }
                        ?.joinToString(separator = PathSeparator) { it.name },
                isContentScrolled = listState.canScrollBackward,
                scrollBehavior = scrollBehavior,
                hazeState = hazeState,
                navigationIcon = {
                    IconButton(onClick = navigation.onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    if (folder != null) {
                        FilledTonalIconToggleButton(
                            checked = folder.isPinned,
                            onCheckedChange = { onAction(FolderContentsIntent.TogglePinned) },
                            shapes = IconButtonDefaults.toggleableShapes(),
                        ) {
                            Icon(
                                imageVector =
                                    if (folder.isPinned) Icons.Rounded.PushPin
                                    else Icons.Outlined.PushPin,
                                contentDescription =
                                    stringResource(
                                        if (folder.isPinned) R.string.folder_unpin
                                        else R.string.folder_pin
                                    ),
                            )
                        }
                        IconButton(
                            onClick = { navigation.onOpenFolderActions(folder.uuid) },
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.more_options),
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            NewFolderButton(
                // The empty state carries its own button.
                visible = uiState.canCreateFolder && page == FolderPage.Contents,
                expanded = isFabExpanded,
                onClick = navigation.onCreateFolder,
                hazeState = hazeState,
            )
        },
    ) { padding ->
        AnimatedContent(
            modifier = Modifier.fillMaxSize().hazeSource(hazeState),
            targetState = page,
            transitionSpec = { motionScheme.contentRevealTransform() },
            label = "FolderPage",
        ) { targetPage ->
            val centered = Modifier.fillMaxSize().padding(padding)
            when (targetPage) {
                FolderPage.Loading ->
                    Box(modifier = centered, contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }

                FolderPage.Empty ->
                    Box(modifier = centered, contentAlignment = Alignment.Center) {
                        ScreenPlaceholderCard(
                            modifier = Modifier.padding(24.dp),
                            title =
                                stringResource(
                                    if (uiState.isRoot) R.string.folders_none_title
                                    else R.string.folder_empty_title
                                ),
                            description =
                                stringResource(
                                    if (uiState.isRoot) R.string.folders_none_desc
                                    else R.string.folder_empty_desc
                                ),
                            icon = Icons.Rounded.FolderOpen,
                            actionText =
                                stringResource(R.string.folder_new).takeIf {
                                    uiState.canCreateFolder
                                },
                            iconAction = Icons.Rounded.CreateNewFolder,
                            onAction = navigation.onCreateFolder,
                        )
                    }

                FolderPage.Contents ->
                    FolderContentsList(
                        uiState = uiState,
                        onAction = onAction,
                        navigation = navigation,
                        listState = listState,
                        selectedDocumentId = selectedDocumentId,
                        contentPadding =
                            PaddingValues(
                                start = padding.calculateStartPadding(layoutDirection),
                                top = padding.calculateTopPadding() + 8.dp,
                                end = padding.calculateEndPadding(layoutDirection),
                                bottom = padding.calculateBottomPadding() + FabClearance,
                            ),
                        hazeState = hazeState,
                    )
            }
        }
    }
}

private const val PathSeparator = "  ›  "

/** The folders first, as they are fewer and lead somewhere; then the documents, in their order. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FolderContentsList(
    uiState: FolderContentsUiState,
    onAction: (FolderContentsIntent) -> Unit,
    navigation: FolderContentsNavigation,
    listState: LazyListState,
    selectedDocumentId: String?,
    contentPadding: PaddingValues,
    hazeState: HazeState,
) {
    val motionScheme = MaterialTheme.motionScheme
    val subfolders = uiState.subfolders
    val documents = uiState.documents

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        if (subfolders.isNotEmpty()) {
            // At the root every folder is listed, so the screen's own title already names them.
            if (!uiState.isRoot) {
                item(key = "folders-header", contentType = "section-header") {
                    SectionHeader(title = stringResource(R.string.folders))
                }
            }
            itemsIndexed(
                items = subfolders,
                key = { _, folder -> "folder-${folder.uuid}" },
                contentType = { _, _ -> "folder" },
            ) { index, folder ->
                FolderListItem(
                    folder = folder,
                    onClick = { navigation.onOpenFolder(folder.uuid) },
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .then(animateItemWith(motionScheme)),
                    shapes =
                        DocucraftShapeDefaults.segmentedListItemShapes(
                            index = index,
                            count = subfolders.size,
                        ),
                    onOpenActions = { navigation.onOpenFolderActions(folder.uuid) },
                )
            }
        }

        if (documents.isNotEmpty()) {
            item(key = "documents-header", contentType = "section-header") {
                SectionHeader(
                    title =
                        stringResource(
                            if (uiState.isRoot) R.string.documents_without_folder
                            else R.string.documents
                        ),
                    modifier = Modifier.padding(top = if (subfolders.isEmpty()) 0.dp else 16.dp),
                    trailing = {
                        SortMenu(
                            currentSortOption = uiState.sort,
                            onSortOptionChange = { onAction(FolderContentsIntent.ApplySort(it)) },
                            hazeState = hazeState,
                        )
                    },
                )
            }
            itemsIndexed(
                items = documents,
                key = { _, document -> document.uuid },
                contentType = { _, _ -> "document" },
            ) { index, document ->
                ScannedDocumentListItem(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .then(animateItemWith(motionScheme)),
                    pdf = document,
                    shapes =
                        DocucraftShapeDefaults.segmentedListItemShapes(
                            index = index,
                            count = documents.size,
                        ),
                    selected = document.uuid == selectedDocumentId,
                    onItemClick = navigation.onOpenDocument,
                    onItemLongClick = { navigation.onOpenDocumentActions(document.uuid) },
                )
            }
        }
    }
}

/**
 * Creating a folder here, floating over the list at the bottom where the thumb is. Lifted by a blur
 * halo, as Home's actions are, and shrunk to its icon while the list is read downwards.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NewFolderButton(
    visible: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    hazeState: HazeState,
) {
    val haloStrength by
        animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            label = "NewFolderHalo",
        )

    SmallExtendedFloatingActionButton(
        text = { Text(text = stringResource(R.string.folder_new)) },
        icon = { Icon(imageVector = Icons.Rounded.CreateNewFolder, contentDescription = null) },
        onClick = onClick,
        expanded = expanded,
        modifier =
            Modifier.blurHalo(
                    state = hazeState,
                    shape = FloatingActionButtonDefaults.smallExtendedFabShape,
                    strength = haloStrength,
                )
                .animateFloatingActionButton(visible = visible, alignment = Alignment.BottomEnd),
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
}

@PreviewLightDark
@Composable
private fun FolderContentsPreview() {
    DocucraftTheme {
        val parent = previewFolder("a", "Home", parentUuid = null)
        val folder = previewFolder("b", "Taxes 2026", parentUuid = "a", pinned = true)
        FolderContentsContent(
            uiState =
                FolderContentsUiState(
                    isRoot = false,
                    folder = folder,
                    path = listOf(parent, folder),
                    subfolders =
                        listOf(
                            previewFolder(
                                "c",
                                "Receipts",
                                "b",
                                color = "amber",
                                icon = "receipt_long",
                            ),
                            previewFolder(
                                "d",
                                "Bank",
                                "b",
                                color = "teal",
                                icon = "account_balance",
                            ),
                        ),
                    documents = DocumentPreviewData.documents,
                    isLoading = false,
                    canCreateFolder = true,
                ),
            onAction = {},
            navigation = FolderContentsNavigation(),
        )
    }
}

@PreviewLightDark
@Composable
private fun FolderContentsEmptyPreview() {
    DocucraftTheme {
        FolderContentsContent(
            uiState =
                FolderContentsUiState(isRoot = true, isLoading = false, canCreateFolder = true),
            onAction = {},
            navigation = FolderContentsNavigation(),
        )
    }
}

internal fun previewFolder(
    uuid: String,
    name: String,
    parentUuid: String?,
    color: String? = null,
    icon: String? = null,
    pinned: Boolean = false,
) =
    Folder(
        uuid = uuid,
        name = name,
        parentUuid = parentUuid,
        color = color,
        icon = icon,
        pinnedAtEpochMillis = if (pinned) 1L else null,
        sort = null,
        createdAtEpochMillis = 0L,
        documentCount = 3,
        folderCount = 1,
    )
