/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.components.ScreenPlaceholderCard
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftShapeDefaults
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
import com.bobbyesp.docucraft.core.presentation.utilities.modifier.customOverscroll
import com.bobbyesp.docucraft.feature.docscanner.domain.SortOption
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.card.ScannedDocumentListItem
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeIntent
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeStatus
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeUiState
import com.bobbyesp.docucraft.feature.docscanner.presentation.preview.DocumentPreviewData
import kotlin.math.roundToInt

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
 * Room for the last document to scroll clear of the scan button: a medium FAB (80dp) and the
 * scaffold's margin under it, plus the list's own gap.
 */
private val ScanButtonClearance = 112.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeContent(
    uiState: HomeUiState,
    onAction: (HomeIntent) -> Unit,
    onOpenDocument: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDocumentActions: (String) -> Unit,
    modifier: Modifier = Modifier,
    selectedDocumentId: String? = null,
) {
    val page = uiState.page
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val focusManager = LocalFocusManager.current
    val motionScheme = MaterialTheme.motionScheme

    var isSearchFocused by remember { mutableStateOf(false) }

    // Collapsed while the user reads down the list, extended again as soon as they head back up.
    val isScanButtonExpanded by remember {
        derivedStateOf { !listState.lastScrolledForward || !listState.canScrollBackward }
    }

    Scaffold(
        modifier =
            modifier.nestedScroll(scrollBehavior.nestedScrollConnection).pointerInput(Unit) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            },
        topBar = {
            HomeTopBar(
                documentCount = uiState.visibleDocuments.size.takeIf { page == HomePage.Documents },
                isContentScrolled = listState.canScrollBackward,
                scrollBehavior = scrollBehavior,
                onOpenSettings = onOpenSettings,
            ) {
                AnimatedVisibility(
                    visible = page == HomePage.Documents,
                    enter =
                        expandVertically(motionScheme.defaultSpatialSpec()) +
                            fadeIn(motionScheme.defaultEffectsSpec()),
                    exit =
                        shrinkVertically(motionScheme.fastSpatialSpec()) +
                            fadeOut(motionScheme.fastEffectsSpec()),
                ) {
                    Column(
                        // The app bar keeps clear of a cutout at the side; so must what hangs from
                        // it, or in landscape the search field runs under the camera.
                        modifier =
                            Modifier.windowInsetsPadding(
                                    TopAppBarDefaults.windowInsets.only(
                                        WindowInsetsSides.Horizontal
                                    )
                                )
                                .padding(horizontal = 16.dp)
                                .padding(top = 4.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        HomeSearchField(
                            query = uiState.searchQuery,
                            onQueryChange = { onAction(HomeIntent.UpdateSearch(it)) },
                            onClear = { onAction(HomeIntent.ClearSearch) },
                            onFocusChange = { isSearchFocused = it },
                        )
                        SortControls(
                            currentSortOption = uiState.filterOptions.sortBy,
                            onSortOptionChange = { onAction(HomeIntent.ApplySort(it)) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            MediumExtendedFloatingActionButton(
                text = { Text(text = stringResource(id = R.string.scan)) },
                icon = {
                    if (uiState.isScanning) {
                        LoadingIndicator(
                            modifier = Modifier.size(28.dp),
                            color = LocalContentColor.current,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.DocumentScanner,
                            contentDescription = stringResource(id = R.string.doc_scan_new),
                        )
                    }
                },
                onClick = { if (!uiState.isScanning) onAction(HomeIntent.LaunchScanner) },
                expanded = isScanButtonExpanded,
                // The empty state carries its own scan button, and while typing the keyboard is
                // where the thumb is: in both cases this one would only be in the way.
                modifier =
                    Modifier.animateFloatingActionButton(
                        visible = page == HomePage.Documents && !isSearchFocused,
                        alignment = Alignment.BottomEnd,
                    ),
            )
        },
    ) { padding ->
        AnimatedContent(
            modifier = Modifier.padding(padding),
            targetState = page,
            transitionSpec = {
                (fadeIn(motionScheme.defaultEffectsSpec()) +
                    scaleIn(motionScheme.defaultSpatialSpec(), initialScale = 0.92f)) togetherWith
                    fadeOut(motionScheme.fastEffectsSpec())
            },
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
                    ScannedDocumentsList(
                        scannedDocuments = uiState.visibleDocuments,
                        searchQuery = uiState.searchQuery,
                        onClearSearch = { onAction(HomeIntent.ClearSearch) },
                        onOpenDocument = onOpenDocument,
                        onOpenDocumentActions = onOpenDocumentActions,
                        listState = listState,
                        selectedDocumentId = selectedDocumentId,
                    )
            }
        }
    }
}

/**
 * The expressive large app bar, with search and sorting under it as one block.
 *
 * The whole block takes one tone, and moves to a container tone once the list scrolls beneath it,
 * rather than the app bar alone changing and leaving a seam above the search field.
 *
 * @param documentCount shown as the subtitle, or null when there is no list to count.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeTopBar(
    documentCount: Int?,
    isContentScrolled: Boolean,
    scrollBehavior: TopAppBarScrollBehavior,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    tools: @Composable () -> Unit,
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

    Column(modifier = modifier.background(containerColor)) {
        LargeFlexibleTopAppBar(
            title = { Text(text = stringResource(id = R.string.app_name)) },
            subtitle =
                documentCount?.let { count ->
                    { Text(text = pluralStringResource(R.plurals.doc_n_documents, count, count)) }
                },
            actions = {
                IconButton(onClick = onOpenSettings, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = stringResource(id = R.string.settings),
                    )
                }
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            scrollBehavior = scrollBehavior,
        )
        tools()
    }
}

/**
 * A search field in the shape and tone of a Material search bar.
 *
 * Not the `SearchBar` component: that one opens a separate search view to type in, and disables the
 * keyboard in its collapsed form. Here the query filters the list in place, under the field.
 */
@Composable
private fun HomeSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val motionScheme = MaterialTheme.motionScheme
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh

    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth().onFocusChanged { onFocusChange(it.isFocused) },
        placeholder = {
            Text(
                text = stringResource(R.string.search_documents),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = stringResource(R.string.search),
            )
        },
        trailingIcon = {
            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter =
                    fadeIn(motionScheme.fastEffectsSpec()) +
                        scaleIn(motionScheme.fastSpatialSpec()),
                exit =
                    fadeOut(motionScheme.fastEffectsSpec()) +
                        scaleOut(motionScheme.fastSpatialSpec()),
            ) {
                IconButton(onClick = onClear, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        imageVector = Icons.Rounded.Clear,
                        contentDescription = stringResource(R.string.clear_search),
                    )
                }
            }
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        shape = SearchBarDefaults.inputFieldShape,
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = containerColor,
                unfocusedContainerColor = containerColor,
                disabledContainerColor = containerColor,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
    )
}

/**
 * What to sort by, as a connected button group, and which way, as one button whose arrow turns.
 *
 * The criteria are one exclusive choice, which is what a connected group says: its buttons touch,
 * and the chosen one fills and rounds fully. No check icon as well: in a pane beside the viewer, or
 * with a longer translation, the three buttons are narrow enough that it squeezed the label to an
 * ellipsis.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SortControls(
    currentSortOption: SortOption,
    onSortOptionChange: (SortOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val motionScheme = MaterialTheme.motionScheme
    val changeSort = { sortOption: SortOption ->
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
        onSortOptionChange(sortOption)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val criteria = SortOption.Criteria.entries

        Row(
            modifier = Modifier.weight(1f).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            criteria.forEachIndexed { index, criterion ->
                val checked = criterion == currentSortOption.criteria

                ToggleButton(
                    checked = checked,
                    onCheckedChange = { isChecked ->
                        if (isChecked) changeSort(currentSortOption.copy(criteria = criterion))
                    },
                    modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                    // The search field's tone: the default, `surfaceContainer`, is also the top
                    // bar's once the list scrolls, and the unchosen buttons vanished into it.
                    colors =
                        ToggleButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                    shapes =
                        when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            criteria.lastIndex ->
                                ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                ) {
                    Text(
                        text = criterion.label(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        val ascending = currentSortOption.order == SortOption.Order.ASC
        val arrowRotation by
            animateFloatAsState(
                targetValue = if (ascending) 0f else 180f,
                animationSpec = motionScheme.defaultSpatialSpec(),
                label = "SortOrderArrow",
            )

        FilledTonalIconButton(
            onClick = {
                changeSort(currentSortOption.copy(order = currentSortOption.order.reverse()))
            },
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowUpward,
                contentDescription =
                    stringResource(
                        if (ascending) R.string.sort_ascending else R.string.sort_descending
                    ),
                modifier = Modifier.graphicsLayer { rotationZ = arrowRotation },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScannedDocumentsList(
    scannedDocuments: List<ScannedDocument>,
    searchQuery: String,
    onClearSearch: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onOpenDocumentActions: (String) -> Unit,
    listState: LazyListState,
    selectedDocumentId: String? = null,
) {
    val motionScheme = MaterialTheme.motionScheme

    AnimatedContent(
        targetState = scannedDocuments.isEmpty(),
        transitionSpec = {
            fadeIn(motionScheme.defaultEffectsSpec()) togetherWith
                fadeOut(motionScheme.fastEffectsSpec())
        },
        label = "DocumentsOrNoMatches",
    ) { noMatches ->
        if (noMatches) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                NoMatchesContent(query = searchQuery, onClearSearch = onClearSearch)
            }
        } else {
            var overscrollOffset by remember { mutableFloatStateOf(0f) }

            LazyColumn(
                modifier =
                    Modifier.fillMaxSize()
                        .customOverscroll(
                            listState = listState,
                            onNewOverscrollAmount = { overscrollOffset = it },
                        )
                        .offset { IntOffset(0, overscrollOffset.roundToInt()) },
                state = listState,
                contentPadding =
                    PaddingValues(
                        start = 16.dp,
                        top = 8.dp,
                        end = 16.dp,
                        bottom = ScanButtonClearance,
                    ),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                itemsIndexed(
                    items = scannedDocuments,
                    key = { _, scannedDocument -> scannedDocument.uuid },
                    contentType = { _, _ -> "document" },
                ) { index, scannedDocument ->
                    ScannedDocumentListItem(
                        modifier =
                            Modifier.fillMaxWidth()
                                .animateItem(
                                    fadeInSpec = motionScheme.defaultEffectsSpec(),
                                    placementSpec = motionScheme.defaultSpatialSpec(),
                                    fadeOutSpec = motionScheme.fastEffectsSpec(),
                                ),
                        pdf = scannedDocument,
                        shapes =
                            DocucraftShapeDefaults.segmentedListItemShapes(
                                index = index,
                                count = scannedDocuments.size,
                            ),
                        selected = scannedDocument.uuid == selectedDocumentId,
                        onItemClick = onOpenDocument,
                        onItemLongClick = { onOpenDocumentActions(scannedDocument.uuid) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NoMatchesContent(query: String, onClearSearch: () -> Unit) {
    ScreenPlaceholderCard(
        modifier = Modifier.padding(24.dp),
        title = stringResource(R.string.doc_no_matches),
        description = stringResource(R.string.doc_no_matches_desc, query),
        icon = Icons.Rounded.SearchOff,
        actionText = stringResource(R.string.clear_search).takeIf { query.isNotEmpty() },
        iconAction = Icons.Rounded.Clear,
        onAction = onClearSearch,
    )
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
                ),
            onAction = {},
            onOpenDocument = {},
            onOpenSettings = {},
            onOpenDocumentActions = {},
            selectedDocumentId = DocumentPreviewData.documents.first().uuid,
        )
    }
}

@PreviewLightDark
@Composable
private fun HomeContentNoMatchesPreview() {
    DocucraftTheme {
        HomeContent(
            uiState =
                HomeUiState(status = HomeStatus.Idle, hasDocuments = true, searchQuery = "invoice"),
            onAction = {},
            onOpenDocument = {},
            onOpenSettings = {},
            onOpenDocumentActions = {},
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
            onOpenDocumentActions = {},
        )
    }
}
