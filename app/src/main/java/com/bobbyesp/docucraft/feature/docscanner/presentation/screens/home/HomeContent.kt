/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.components.ScreenPlaceholderCard
import com.bobbyesp.docucraft.core.presentation.components.image.AsyncImage
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftShapeDefaults
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
import com.bobbyesp.docucraft.core.util.animateItemWith
import com.bobbyesp.docucraft.core.util.contentRevealTransform
import com.bobbyesp.docucraft.feature.docscanner.domain.SortOption
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.card.ScannedDocumentListItem
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeIntent
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeStatus
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeUiState
import com.bobbyesp.docucraft.feature.docscanner.presentation.preview.DocumentPreviewData
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.search.DocumentSearchBarButton
import com.skydoves.landscapist.ImageOptions

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
    selectedDocumentId: String? = null,
    actionsInTopBar: Boolean = false,
) {
    val page = uiState.page
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val motionScheme = MaterialTheme.motionScheme

    // Collapsed while the user reads down the list, extended again as soon as they head back up.
    val isScanButtonExpanded by remember {
        derivedStateOf { !listState.lastScrolledForward || !listState.canScrollBackward }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            HomeTopBar(
                isContentScrolled = listState.canScrollBackward,
                scrollBehavior = scrollBehavior,
                onOpenSettings = onOpenSettings,
                documentActions =
                    if (actionsInTopBar && page == HomePage.Documents) {
                        {
                            TopBarDocumentActions(
                                isScanning = uiState.isScanning,
                                onOpenSearch = onOpenSearch,
                                onScan = { onAction(HomeIntent.LaunchScanner) },
                            )
                        }
                    } else {
                        null
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
                )
            }
        },
    ) { padding ->
        AnimatedContent(
            modifier = Modifier.padding(padding),
            targetState = page,
            transitionSpec = { motionScheme.contentRevealTransform() },
            label = "HomePage",
        ) { targetPage ->
            when (targetPage) {
                HomePage.Loading ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }

                HomePage.Error ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ErrorContent(errorMessage = uiState.errorMessage)
                    }

                HomePage.Empty ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyStateScreen(onScanDocument = { onAction(HomeIntent.LaunchScanner) })
                    }

                HomePage.Documents ->
                    DocumentsPage(
                        documents = uiState.visibleDocuments,
                        recentDocuments = uiState.recentDocuments,
                        sortOption = uiState.filterOptions.sortBy,
                        onSortOptionChange = { onAction(HomeIntent.ApplySort(it)) },
                        onOpenDocument = onOpenDocument,
                        onOpenDocumentActions = onOpenDocumentActions,
                        listState = listState,
                        selectedDocumentId = selectedDocumentId,
                        bottomClearance = if (actionsInTopBar) 16.dp else BottomActionsClearance,
                    )
            }
        }
    }
}

/**
 * The expressive large app bar. It takes a container tone once the list scrolls beneath it, eased
 * rather than switched.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeTopBar(
    isContentScrolled: Boolean,
    scrollBehavior: TopAppBarScrollBehavior,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    documentActions: (@Composable () -> Unit)? = null,
) {
    val containerColor by
        animateColorAsState(
            targetValue =
                if (isContentScrolled) {
                    MaterialTheme.colorScheme.surfaceContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            label = "HomeTopBarContainer",
        )

    LargeFlexibleTopAppBar(
        title = { Text(text = stringResource(id = R.string.app_name)) },
        modifier = modifier,
        actions = {
            documentActions?.invoke()
            IconButton(onClick = onOpenSettings, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = stringResource(id = R.string.settings),
                )
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = containerColor,
                scrolledContainerColor = containerColor,
            ),
        scrollBehavior = scrollBehavior,
    )
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
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeBottomActions(
    visible: Boolean,
    isScanning: Boolean,
    isScanButtonExpanded: Boolean,
    onOpenSearch: () -> Unit,
    onScan: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(start = 32.dp)
                .animateFloatingActionButton(visible = visible, alignment = Alignment.BottomEnd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DocumentSearchBarButton(onClick = onOpenSearch, modifier = Modifier.weight(1f))

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
        )
    }
}

/**
 * Recents first, then every document in the chosen order. Categories will sit between the two once
 * the catalogue has them.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DocumentsPage(
    documents: List<ScannedDocument>,
    recentDocuments: List<ScannedDocument>,
    sortOption: SortOption,
    onSortOptionChange: (SortOption) -> Unit,
    onOpenDocument: (String) -> Unit,
    onOpenDocumentActions: (String) -> Unit,
    listState: LazyListState,
    selectedDocumentId: String?,
    bottomClearance: Dp,
) {
    val motionScheme = MaterialTheme.motionScheme

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(top = 8.dp, bottom = bottomClearance),
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

        item(key = "documents-header", contentType = "section-header") {
            SectionHeader(
                title = stringResource(R.string.documents),
                trailing = {
                    SortMenu(
                        currentSortOption = sortOption,
                        onSortOptionChange = onSortOptionChange,
                    )
                },
            )
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
                onItemClick = onOpenDocument,
                onItemLongClick = { onOpenDocumentActions(scannedDocument.uuid) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(start = 20.dp, end = if (trailing != null) 8.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMediumEmphasized,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/**
 * The latest documents as their first pages, where a thumbnail tells them apart faster than a
 * title. A multi-browse carousel: one large, the next ones shrinking, so it reads as more to swipe
 * through rather than a row that ends at the edge.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun RecentDocumentsCarousel(
    documents: List<ScannedDocument>,
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
        val document = documents[index]
        val title = document.title ?: document.filename

        Box(
            modifier =
                Modifier.fillMaxSize()
                    .maskClip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .combinedClickable(
                        role = Role.Button,
                        onClickLabel = openLabel,
                        onLongClickLabel = moreOptionsLabel,
                        onClick = { onOpenDocument(document.uuid) },
                        onLongClick = { onOpenDocumentActions(document.uuid) },
                    )
                    // The title is faded out on the narrow items, but a screen reader should still
                    // name every one of them.
                    .semantics { contentDescription = title }
        ) {
            if (LocalInspectionMode.current || document.thumbnail == null) {
                Icon(
                    imageVector = Icons.Rounded.Description,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.Center).size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                AsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    imageModel = document.thumbnail.value,
                    shape = RectangleShape,
                    // A page's heading is at its top, and is what identifies it.
                    imageOptions =
                        ImageOptions(
                            alignment = Alignment.TopCenter,
                            contentDescription = null,
                        ),
                )
            }

            // Over a photo, not a theme surface: white on a dark scrim reads on any page, which no
            // color-scheme role can promise.
            Text(
                text = title,
                modifier =
                    Modifier.align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .graphicsLayer {
                            val info = carouselItemDrawInfo
                            val range = info.maxSize - info.minSize
                            alpha =
                                if (range <= 0f) 1f
                                else ((info.size - info.minSize) / range).coerceIn(0f, 1f)
                        }
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

/**
 * The current order, named on the button itself, and a menu to change it: criteria in one group,
 * direction in the other. A transient popup anchored here, not a destination.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SortMenu(
    currentSortOption: SortOption,
    onSortOptionChange: (SortOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current
    var expanded by remember { mutableStateOf(false) }

    val ascending = currentSortOption.order == SortOption.Order.ASC
    val arrowRotation by
        animateFloatAsState(
            targetValue = if (ascending) 0f else 180f,
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            label = "SortOrderArrow",
        )

    val changeSort = { sortOption: SortOption ->
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
        onSortOptionChange(sortOption)
        expanded = false
    }

    val sortByLabel = stringResource(R.string.sort_by)
    val orderLabel =
        stringResource(if (ascending) R.string.sort_ascending else R.string.sort_descending)
    val criterionLabel = currentSortOption.criteria.label()

    Box(modifier = modifier) {
        TextButton(
            onClick = { expanded = true },
            shapes = ButtonDefaults.shapes(),
            modifier =
                Modifier.semantics {
                    contentDescription = sortByLabel
                    stateDescription = "$criterionLabel, $orderLabel"
                },
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Sort,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            Text(text = criterionLabel)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Rounded.ArrowUpward,
                contentDescription = null,
                modifier =
                    Modifier.size(ButtonDefaults.IconSize).graphicsLayer {
                        rotationZ = arrowRotation
                    },
            )
        }

        DropdownMenuPopup(expanded = expanded, onDismissRequest = { expanded = false }) {
            val criteria = SortOption.Criteria.entries

            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 2)) {
                criteria.forEachIndexed { index, criterion ->
                    SelectableDropdownMenuItem(
                        selected = criterion == currentSortOption.criteria,
                        onClick = { changeSort(currentSortOption.copy(criteria = criterion)) },
                        text = { Text(text = criterion.label()) },
                        shapes = MenuDefaults.itemShape(index = index, count = criteria.size),
                        selectedLeadingIcon = {
                            Icon(Icons.Rounded.Check, contentDescription = null)
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(MenuDefaults.GroupSpacing))

            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 1, count = 2)) {
                val orders =
                    listOf(
                        SortOption.Order.ASC to
                            (R.string.sort_ascending to Icons.Rounded.ArrowUpward),
                        SortOption.Order.DESC to
                            (R.string.sort_descending to Icons.Rounded.ArrowDownward),
                    )
                orders.forEachIndexed { index, (order, labelAndIcon) ->
                    val (label, icon) = labelAndIcon
                    SelectableDropdownMenuItem(
                        selected = order == currentSortOption.order,
                        onClick = { changeSort(currentSortOption.copy(order = order)) },
                        text = { Text(text = stringResource(label)) },
                        shapes = MenuDefaults.itemShape(index = index, count = orders.size),
                        leadingIcon = { Icon(icon, contentDescription = null) },
                        selectedLeadingIcon = {
                            Icon(Icons.Rounded.Check, contentDescription = null)
                        },
                    )
                }
            }
        }
    }
}

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
                    recentDocuments = DocumentPreviewData.documents,
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
