/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.blur.BlurRadiusSpec
import androidx.compose.ui.graphics.blur.BlurStop
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.components.ScreenPlaceholderCard
import com.bobbyesp.docucraft.core.presentation.components.image.AsyncImage
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftBlurDefaults
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftShapeDefaults
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
import com.bobbyesp.docucraft.core.presentation.theme.blurHalo
import com.bobbyesp.docucraft.core.util.animateItemWith
import com.bobbyesp.docucraft.core.util.contentRevealTransform
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Folder
import com.bobbyesp.docucraft.feature.docscanner.domain.model.FolderIcon
import com.bobbyesp.docucraft.feature.docscanner.domain.model.RecentDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Tag
import com.bobbyesp.docucraft.feature.docscanner.domain.model.labelColor
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.card.ScannedDocumentListItem
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.list.FrostedLargeTopAppBar
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.list.SectionHeader
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.list.SortMenu
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.organization.FolderBadge
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.organization.FolderCard
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.organization.TagDot
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.organization.TagFilterChip
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeIntent
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeStatus
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeUiState
import com.bobbyesp.docucraft.feature.docscanner.presentation.preview.DocumentPreviewData
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.search.DocumentSearchBarButton
import com.bobbyesp.docucraft.feature.shared.presentation.Measurements
import com.skydoves.landscapist.ImageOptions
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch

/** Which of Home's faces is showing. Its own type so a change between any two of them animates. */
private enum class HomePage {
    Loading,
    Error,
    Empty,
    Documents,
}

private val HomeUiState.page: HomePage
    get() =
        when (status) {
            HomeStatus.Loading -> HomePage.Loading
            is HomeStatus.Error -> HomePage.Error
            HomeStatus.Idle -> if (hasDocuments) HomePage.Documents else HomePage.Empty
        }

/**
 * Room for the last document to scroll clear of the search bar and scan button floating over it:
 * their height (56dp), the scaffold's margin under them, and the list's own gap.
 */
private val BottomActionsClearance = 88.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeContent(
    uiState: HomeUiState,
    onAction: (HomeIntent) -> Unit,
    onOpenDocument: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenDocumentActions: (String) -> Unit,
    modifier: Modifier = Modifier,
    organization: HomeOrganizationActions = HomeOrganizationActions(),
    selectedDocumentId: String? = null,
    actionsInTopBar: Boolean = false,
) {
    val page = uiState.page
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val motionScheme = MaterialTheme.motionScheme
    val layoutDirection = LocalLayoutDirection.current

    // Home's content, recorded for what floats over it to frost: the app bar, the search bar and
    // the sort menu. It fills the whole scaffold and scrolls beneath them, padded, not inset.
    val hazeState = rememberHazeState()

    // Collapsed while the user reads down the list, extended again as soon as they head back up.
    val isScanButtonExpanded by remember {
        derivedStateOf { !listState.lastScrolledForward || !listState.canScrollBackward }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            FrostedLargeTopAppBar(
                title = stringResource(id = R.string.app_name),
                isContentScrolled = listState.canScrollBackward,
                scrollBehavior = scrollBehavior,
                hazeState = hazeState,
                actions = {
                    if (actionsInTopBar && page == HomePage.Documents) {
                        TopBarDocumentActions(
                            isScanning = uiState.isScanning,
                            onOpenSearch = onOpenSearch,
                            onScan = { onAction(HomeIntent.LaunchScanner) },
                        )
                    }
                    IconButton(onClick = onOpenSettings, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(id = R.string.settings),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (!actionsInTopBar) {
                HomeBottomActions(
                    // The empty state carries its own scan button, and there is nothing to search.
                    visible = page == HomePage.Documents,
                    isScanning = uiState.isScanning,
                    isScanButtonExpanded = isScanButtonExpanded,
                    onOpenSearch = onOpenSearch,
                    onScan = { onAction(HomeIntent.LaunchScanner) },
                    hazeState = hazeState,
                )
            }
        },
    ) { padding ->
        AnimatedContent(
            modifier = Modifier.fillMaxSize().hazeSource(hazeState),
            targetState = page,
            transitionSpec = { motionScheme.contentRevealTransform() },
            label = "HomePage",
        ) { targetPage ->
            val centered = Modifier.fillMaxSize().padding(padding)
            when (targetPage) {
                HomePage.Loading ->
                    Box(modifier = centered, contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }

                HomePage.Error ->
                    Box(modifier = centered, contentAlignment = Alignment.Center) {
                        ErrorContent(errorMessage = uiState.errorMessage)
                    }

                HomePage.Empty ->
                    Box(modifier = centered, contentAlignment = Alignment.Center) {
                        EmptyStateScreen(onScanDocument = { onAction(HomeIntent.LaunchScanner) })
                    }

                HomePage.Documents -> {
                    val bottomClearance = if (actionsInTopBar) 16.dp else BottomActionsClearance
                    DocumentsPage(
                        uiState = uiState,
                        onAction = onAction,
                        organization = organization,
                        onOpenDocument = onOpenDocument,
                        onOpenDocumentActions = onOpenDocumentActions,
                        listState = listState,
                        selectedDocumentId = selectedDocumentId,
                        contentPadding =
                            PaddingValues(
                                start = padding.calculateStartPadding(layoutDirection),
                                top = padding.calculateTopPadding() + 8.dp,
                                end = padding.calculateEndPadding(layoutDirection),
                                bottom = padding.calculateBottomPadding() + bottomClearance,
                            ),
                        hazeState = hazeState,
                    )
                }
            }
        }
    }
}

/**
 * Search and scan as app bar actions, for when Home shares the window with a document. Floating at
 * the bottom of a pane that short, they covered most of what little list it showed.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TopBarDocumentActions(
    isScanning: Boolean,
    onOpenSearch: () -> Unit,
    onScan: () -> Unit,
) {
    IconButton(onClick = onOpenSearch, shapes = IconButtonDefaults.shapes()) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = stringResource(id = R.string.search_documents),
        )
    }
    FilledIconButton(
        onClick = { if (!isScanning) onScan() },
        shapes = IconButtonDefaults.shapes(),
    ) {
        if (isScanning) {
            LoadingIndicator(modifier = Modifier.size(24.dp), color = LocalContentColor.current)
        } else {
            Icon(
                imageVector = Icons.Rounded.DocumentScanner,
                contentDescription = stringResource(id = R.string.doc_scan_new),
            )
        }
    }
}

/**
 * Search and scan, side by side at the bottom where the thumb is. The scan button shrinks to its
 * icon while the list is read downwards, and the search bar takes the room it leaves.
 *
 * Lifted off the list by one blur halo around the pair, not a shadow under each: two halos side by
 * side would each blur over the other's button, and one follows the pair as the scan button changes
 * width. The halo comes into focus with them rather than inside their animation, which would scale
 * it with their spring and clip it to their bounds while it ran.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeBottomActions(
    visible: Boolean,
    isScanning: Boolean,
    isScanButtonExpanded: Boolean,
    onOpenSearch: () -> Unit,
    onScan: () -> Unit,
    hazeState: HazeState,
) {
    val haloStrength by
        animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            label = "HomeBottomActionsHalo",
        )

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(start = 32.dp)
                .blurHalo(state = hazeState, shape = CircleShape, strength = haloStrength)
                .animateFloatingActionButton(visible = visible, alignment = Alignment.BottomEnd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DocumentSearchBarButton(
            onClick = onOpenSearch,
            hazeState = hazeState,
            modifier = Modifier.weight(1f),
        )

        SmallExtendedFloatingActionButton(
            text = { Text(text = stringResource(id = R.string.scan)) },
            icon = {
                if (isScanning) {
                    LoadingIndicator(
                        modifier = Modifier.size(24.dp),
                        color = LocalContentColor.current,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.DocumentScanner,
                        contentDescription = stringResource(id = R.string.doc_scan_new),
                    )
                }
            },
            onClick = { if (!isScanning) onScan() },
            expanded = isScanButtonExpanded,
            elevation =
                if (DocucraftBlurDefaults.isHaloSupported) FlatFabElevation
                else FloatingActionButtonDefaults.elevation(),
        )
    }
}

/** A FAB the halo lifts instead, at rest and when pressed alike. */
private val FlatFabElevation
    @Composable
    get() =
        FloatingActionButtonDefaults.elevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
        )

/**
 * Where Home sends the user to organize the library. Navigation, so none of it is an intent: Home
 * only says where the user asked to go.
 *
 * @property onOpenFolder a folder, or the root of the library for `null`.
 */
@Immutable
data class HomeOrganizationActions(
    val onOpenFolder: (String?) -> Unit = {},
    val onOpenFolderActions: (String) -> Unit = {},
    val onCreateFolder: () -> Unit = {},
    val onManageTags: () -> Unit = {},
)

/**
 * Home, top to bottom: what was used last; the folders pinned here; a shelf for each tag the user
 * chose; and then every document, in the chosen order and narrowed down by the chosen filters.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DocumentsPage(
    uiState: HomeUiState,
    onAction: (HomeIntent) -> Unit,
    organization: HomeOrganizationActions,
    onOpenDocument: (String) -> Unit,
    onOpenDocumentActions: (String) -> Unit,
    listState: LazyListState,
    selectedDocumentId: String?,
    contentPadding: PaddingValues,
    hazeState: HazeState,
) {
    val motionScheme = MaterialTheme.motionScheme
    val scope = rememberCoroutineScope()
    val documents = uiState.visibleDocuments
    val recentDocuments = uiState.recentDocuments
    val filters = uiState.filterOptions

    // Where the list of every document starts, after the two items of each section above it.
    val libraryHeaderIndex =
        (if (recentDocuments.isNotEmpty()) 2 else 0) + 2 + 2 * uiState.tagSections.size

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        if (recentDocuments.isNotEmpty()) {
            item(key = "recents-header", contentType = "section-header") {
                SectionHeader(title = stringResource(R.string.recents))
            }
            item(key = "recents", contentType = "recents") {
                RecentDocumentsCarousel(
                    documents = recentDocuments,
                    onOpenDocument = onOpenDocument,
                    onOpenDocumentActions = onOpenDocumentActions,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
        }

        // Always here, pinned folders or not: it is also the way into the folders.
        item(key = "folders-header", contentType = "section-header") {
            SectionHeader(
                title = stringResource(R.string.folders),
                trailing = {
                    TextButton(
                        onClick = { organization.onOpenFolder(null) },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(text = stringResource(R.string.see_all))
                    }
                },
            )
        }
        item(key = "folders", contentType = "folders") {
            PinnedFoldersRow(
                folders = uiState.pinnedFolders,
                onOpenFolder = organization.onOpenFolder,
                onOpenFolderActions = organization.onOpenFolderActions,
                onCreateFolder = organization.onCreateFolder,
                modifier = Modifier.padding(bottom = 16.dp).then(animateItemWith(motionScheme)),
            )
        }

        for (section in uiState.tagSections) {
            item(key = "tag-header-${section.tag.uuid}", contentType = "section-header") {
                SectionHeader(
                    title = section.tag.name,
                    modifier = animateItemWith(motionScheme),
                    leading = { TagDot(color = section.tag.labelColor) },
                    trailing = {
                        TextButton(
                            onClick = {
                                onAction(HomeIntent.ShowOnlyTag(section.tag.uuid))
                                // Down to the list it now narrows, which is where "all" is.
                                scope.launch {
                                    listState.animateScrollToItem(libraryHeaderIndex)
                                }
                            },
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(text = stringResource(R.string.see_all))
                        }
                    },
                )
            }
            item(key = "tag-${section.tag.uuid}", contentType = "tag-shelf") {
                TagShelf(
                    documents = section.documents,
                    onOpenDocument = onOpenDocument,
                    onOpenDocumentActions = onOpenDocumentActions,
                    modifier = Modifier.padding(bottom = 16.dp).then(animateItemWith(motionScheme)),
                )
            }
        }

        item(key = "documents-header", contentType = "section-header") {
            SectionHeader(
                title = stringResource(R.string.documents),
                trailing = {
                    SortMenu(
                        currentSortOption = filters.sortBy,
                        onSortOptionChange = { onAction(HomeIntent.ApplySort(it)) },
                        hazeState = hazeState,
                    )
                },
            )
        }
        item(key = "documents-filters", contentType = "filters") {
            LibraryFilters(
                tags = uiState.tags,
                favoritesOnly = filters.favoritesOnly,
                selectedTagUuids = filters.tagUuids,
                onToggleFavorites = { onAction(HomeIntent.ToggleFavoritesFilter) },
                onToggleTag = { onAction(HomeIntent.ToggleTagFilter(it)) },
                onManageTags = organization.onManageTags,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        if (documents.isEmpty()) {
            item(key = "documents-none", contentType = "placeholder") {
                NothingMatchesFilters(
                    onClearFilters = { onAction(HomeIntent.ClearFilters) },
                    modifier = animateItemWith(motionScheme),
                )
            }
        }

        itemsIndexed(
            items = documents,
            key = { _, scannedDocument -> scannedDocument.uuid },
            contentType = { _, _ -> "document" },
        ) { index, scannedDocument ->
            ScannedDocumentListItem(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .then(animateItemWith(motionScheme)),
                pdf = scannedDocument,
                shapes =
                    DocucraftShapeDefaults.segmentedListItemShapes(
                        index = index,
                        count = documents.size,
                    ),
                selected = scannedDocument.uuid == selectedDocumentId,
                fileMissing = scannedDocument.uuid in uiState.notFoundUuids,
                onItemClick = onOpenDocument,
                onItemLongClick = { onOpenDocumentActions(scannedDocument.uuid) },
            )
        }
    }
}

/**
 * The folders pinned to Home, as cards in their own colors, and a way to make one at the end. With
 * none pinned, the row says what goes here instead of leaving a gap under its header.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PinnedFoldersRow(
    folders: List<Folder>,
    onOpenFolder: (String?) -> Unit,
    onOpenFolderActions: (String) -> Unit,
    onCreateFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme

    if (folders.isEmpty()) {
        Surface(
            modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = DocucraftShapeDefaults.cardShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                FolderBadge(icon = FolderIcon.Default, color = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.pinned_folders_empty_title),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                    Text(
                        text = stringResource(R.string.pinned_folders_empty_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalIconButton(
                    onClick = onCreateFolder,
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CreateNewFolder,
                        contentDescription = stringResource(R.string.folder_new),
                    )
                }
            }
        }
        return
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = folders, key = { it.uuid }, contentType = { "folder" }) { folder ->
            FolderCard(
                folder = folder,
                onClick = { onOpenFolder(folder.uuid) },
                onLongClick = { onOpenFolderActions(folder.uuid) },
                modifier = animateItemWith(motionScheme),
            )
        }
    }
}

/**
 * The documents of a tag, side by side as their first pages: a shelf, shorter than Recents so that
 * the two do not read as the same thing twice.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TagShelf(
    documents: List<Document.Managed>,
    onOpenDocument: (String) -> Unit,
    onOpenDocumentActions: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme

    if (documents.isEmpty()) {
        Text(
            text = stringResource(R.string.tag_section_empty),
            modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = documents, key = { it.uuid }, contentType = { "document" }) { document ->
            Column(
                modifier =
                    Modifier.width(112.dp)
                        .then(animateItemWith(motionScheme))
                        .clip(MaterialTheme.shapes.large)
                        .combinedClickable(
                            role = Role.Button,
                            onLongClickLabel = stringResource(R.string.more_options),
                            onLongClick = { onOpenDocumentActions(document.uuid) },
                            onClick = { onOpenDocument(document.uuid) },
                        )
                        .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .aspectRatio(Measurements.A4_RATIO)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    if (LocalInspectionMode.current) {
                        PreviewPlaceholder()
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
                            failure = { PreviewPlaceholder() },
                        )
                    }
                }
                Text(
                    text = document.name,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * What the list of documents is narrowed down by: favorites, and the tags. Chips in a row that
 * scrolls, with the way to the tags themselves at its end.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LibraryFilters(
    tags: List<Tag>,
    favoritesOnly: Boolean,
    selectedTagUuids: Set<String>,
    onToggleFavorites: () -> Unit,
    onToggleTag: (String) -> Unit,
    onManageTags: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "favorites", contentType = "favorites") {
            FilterChip(
                selected = favoritesOnly,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onToggleFavorites()
                },
                label = { Text(text = stringResource(R.string.favorites)) },
                shapes = FilterChipDefaults.shapes(),
                leadingIcon = {
                    Icon(
                        imageVector =
                            if (favoritesOnly) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
            )
        }
        items(items = tags, key = { it.uuid }, contentType = { "tag" }) { tag ->
            TagFilterChip(
                tag = tag,
                selected = tag.uuid in selectedTagUuids,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onToggleTag(tag.uuid)
                },
                modifier = Modifier.animateItem(),
            )
        }
        item(key = "manage-tags", contentType = "manage") {
            AssistChip(
                onClick = onManageTags,
                label = {
                    Text(
                        text =
                            stringResource(
                                if (tags.isEmpty()) R.string.tag_new else R.string.tags_manage
                            )
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Label,
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                    )
                },
            )
        }
    }
}

/** The library has documents, and none of them passes the filters. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NothingMatchesFilters(onClearFilters: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.filters_no_matches),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        FilledTonalButton(onClick = onClearFilters, shapes = ButtonDefaults.shapes()) {
            Text(text = stringResource(R.string.filters_clear))
        }
    }
}

/**
 * The documents used last as their first pages, where a thumbnail tells them apart faster than a
 * title. A multi-browse carousel: one large, the next ones shrinking, so it reads as more to swipe
 * through rather than a row that ends at the edge.
 *
 * A document whose file could not be reached the last time is shown faded, so that it says so
 * before it is tapped rather than failing afterwards.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun RecentDocumentsCarousel(
    documents: List<RecentDocument>,
    onOpenDocument: (String) -> Unit,
    onOpenDocumentActions: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val carouselState = rememberCarouselState { documents.size }
    val openLabel = stringResource(R.string.open)
    val moreOptionsLabel = stringResource(R.string.more_options)

    HorizontalMultiBrowseCarousel(
        state = carouselState,
        preferredItemWidth = 160.dp,
        modifier = modifier.fillMaxWidth().height(224.dp),
        itemSpacing = 8.dp,
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) { index ->
        val recent = documents[index]
        val document = recent.document
        val title = document.name
        // Only a document the app keeps has a preview: another app's file is not read to draw one.
        val thumbnail = (document as? Document.Managed)?.thumbnail
        val contentAlpha = if (recent.isReachable) 1f else UnreachableAlpha

        Box(
            modifier =
                Modifier.fillMaxSize()
                    .maskClip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .combinedClickable(
                        role = Role.Button,
                        onClickLabel = openLabel,
                        onLongClickLabel = moreOptionsLabel,
                        // Another app's document that can no longer be reached has nothing to
                        // show: what can be done about it is offered instead of an error.
                        onClick = {
                            if (document is Document.Linked && !recent.isReachable) {
                                onOpenDocumentActions(document.uuid)
                            } else {
                                onOpenDocument(document.uuid)
                            }
                        },
                        onLongClick = { onOpenDocumentActions(document.uuid) },
                    )
                    // The title is faded out on the narrow items, but a screen reader should still
                    // name every one of them.
                    .semantics { contentDescription = title }
        ) {
            // Outside the image, which goes out of focus towards its bottom: an icon is not a page,
            // and blurred it reads as a rendering fault.
            var hasNoPreview by remember(thumbnail) { mutableStateOf(thumbnail == null) }
            if (LocalInspectionMode.current || hasNoPreview) {
                PreviewPlaceholder(modifier = Modifier.align(Alignment.Center).alpha(contentAlpha))
            }

            if (!LocalInspectionMode.current && thumbnail != null) {
                AsyncImage(
                    modifier =
                        Modifier.fillMaxSize().alpha(contentAlpha).blur {
                            radius = titleBackdropBlur(titleVisibility = titleVisibility)
                        },
                    imageModel = thumbnail,
                    shape = RectangleShape,
                    // A page's heading is at its top, and is what identifies it.
                    imageOptions =
                        ImageOptions(
                            alignment = Alignment.TopCenter,
                            contentDescription = null,
                        ),
                    // A document whose preview cannot be drawn, such as one whose file is gone.
                    failure = { SideEffect { hasNoPreview = true } },
                )
            }

            // Over a photo, not a theme surface: white on a dark scrim reads on any page, which no
            // color-scheme role can promise.
            Text(
                text = title,
                modifier =
                    Modifier.align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .graphicsLayer { alpha = titleVisibility }
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                            )
                        )
                        .padding(start = 16.dp, top = 32.dp, end = 16.dp, bottom = 16.dp),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** How much of a recent document shows when its file could not be reached. */
private const val UnreachableAlpha = 0.38f

/** What a recent document shows where its preview would be, when there is none to show. */
@Composable
private fun PreviewPlaceholder(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Rounded.Description,
        contentDescription = null,
        modifier = modifier.size(48.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * How much of an item's title shows: all of it on the large item, none on the narrowest, which is
 * too narrow to read one. Read in draw and layer blocks, so it follows the carousel's scroll there.
 */
private val CarouselItemScope.titleVisibility: Float
    get() {
        val info = carouselItemDrawInfo
        val range = info.maxSize - info.minSize
        return if (range <= 0f) 1f else ((info.size - info.minSize) / range).coerceIn(0f, 1f)
    }

/** Where, from the top of a thumbnail, it starts going out of focus: just above its title. */
private const val TitleBackdropStart = 0.5f

/**
 * The bottom of a thumbnail out of focus under its title. A scanned page is mostly text, and a
 * title over sharp text reads as more of it; blurred, the page's lines stop competing with it. It
 * fades with the title, so the narrow items, which show none, stay sharp.
 *
 * Android 13 and up, as every spatially varying blur is. Below, the page stays sharp and the dark
 * gradient under the title keeps it legible on its own, as it always did.
 */
private fun titleBackdropBlur(titleVisibility: Float): BlurRadiusSpec =
    BlurRadiusSpec.verticalGradient(
        listOf(
            BlurStop(fraction = 0f, radius = 0.dp),
            BlurStop(fraction = TitleBackdropStart, radius = 0.dp),
            BlurStop(
                fraction = 1f,
                radius = DocucraftBlurDefaults.BehindTitleRadius * titleVisibility,
            ),
        )
    )

@Composable
private fun EmptyStateScreen(onScanDocument: () -> Unit, modifier: Modifier = Modifier) {
    ScreenPlaceholderCard(
        modifier = modifier.padding(24.dp),
        title = stringResource(R.string.no_scanned_documents),
        description = stringResource(R.string.doc_scan_to_see_document_list),
        actionText = stringResource(R.string.doc_scan_new),
        onAction = onScanDocument,
        icon = Icons.Rounded.FileCopy,
        iconAction = Icons.Rounded.CameraAlt,
    )
}

@Composable
private fun ErrorContent(errorMessage: String?) {
    ScreenPlaceholderCard(
        modifier = Modifier.padding(24.dp),
        title = stringResource(id = R.string.unknown_error),
        description = errorMessage ?: stringResource(id = R.string.error_loading_docs),
        icon = Icons.Rounded.Warning,
        isError = true,
    )
}

@PreviewLightDark
@Composable
private fun HomeContentPreview() {
    DocucraftTheme {
        HomeContent(
            uiState =
                HomeUiState(
                    status = HomeStatus.Idle,
                    hasDocuments = true,
                    visibleDocuments = DocumentPreviewData.documents,
                    recentDocuments = DocumentPreviewData.recentDocuments,
                ),
            onAction = {},
            onOpenDocument = {},
            onOpenSettings = {},
            onOpenSearch = {},
            onOpenDocumentActions = {},
            selectedDocumentId = DocumentPreviewData.documents.first().uuid,
        )
    }
}

@PreviewLightDark
@Composable
private fun HomeContentLoadingPreview() {
    DocucraftTheme {
        HomeContent(
            uiState = HomeUiState(status = HomeStatus.Loading),
            onAction = {},
            onOpenDocument = {},
            onOpenSettings = {},
            onOpenSearch = {},
            onOpenDocumentActions = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun HomeContentErrorPreview() {
    DocucraftTheme {
        HomeContent(
            uiState = HomeUiState(status = HomeStatus.Error("Couldn't reach local storage")),
            onAction = {},
            onOpenDocument = {},
            onOpenSettings = {},
            onOpenSearch = {},
            onOpenDocumentActions = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun HomeContentEmptyPreview() {
    DocucraftTheme {
        HomeContent(
            uiState = HomeUiState(status = HomeStatus.Idle, hasDocuments = false),
            onAction = {},
            onOpenDocument = {},
            onOpenSettings = {},
            onOpenSearch = {},
            onOpenDocumentActions = {},
        )
    }
}
