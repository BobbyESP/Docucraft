/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home

import android.text.format.Formatter.formatFileSize
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.motionScheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.components.selectiongroup.SelectionGroupRow
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
import com.bobbyesp.docucraft.feature.docscanner.domain.FilterOptions
import com.bobbyesp.docucraft.feature.docscanner.domain.SortOption
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.card.ScannedDocumentCardPosition
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.card.ScannedDocumentListItem
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeContentState
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeIntent
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeStatus
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeUiState
import com.bobbyesp.docucraft.util.MockData
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Horizontal margin shared by the sort row, the list and every state screen. */
private val CompactMargin = 16.dp
private val MediumMargin = 24.dp

/** Beyond this the content stops growing and centres, so a row's title stays near its metadata. */
private val MaxContentWidth = 640.dp

/** Clears the FAB, which floats above the list. */
private val FabSpacerHeight = 88.dp

/** Local queries usually resolve faster than this; below it, a spinner is just a flash. */
private const val LOADING_INDICATOR_DELAY_MS = 150L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeContent(
    uiState: HomeUiState,
    onAction: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
    selectedDocumentId: String? = null,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Search is a mode, not a persistent control, so its expansion is pure UI state and stays out
    // of the ViewModel. Only the query itself is business state.
    val searchBarState = rememberSearchBarState()
    val queryState = rememberTextFieldState()

    LaunchedEffect(Unit) {
        snapshotFlow { queryState.text.toString() }
            .collect { query -> onAction(HomeIntent.UpdateSearch(query)) }
    }

    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    val isCompactWidth =
        !windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    // A two-row app bar costs an unacceptable share of a short window, so height decides this,
    // not width.
    val isCompactHeight =
        !windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)

    val horizontalMargin = if (isCompactWidth) CompactMargin else MediumMargin
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // transitionSpec is not a @Composable lambda, so the theme's motion spec has to be read here.
    val stateFadeSpec = motionScheme.defaultEffectsSpec<Float>()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            HomeTopBar(
                uiState = uiState,
                scrollBehavior = scrollBehavior,
                useFlexibleBar = !isCompactHeight,
                onSearch = { scope.launch { searchBarState.animateToExpanded() } },
                onSettings = { onAction(HomeIntent.OpenSettings) },
            )
        },
        floatingActionButton = {
            // Hidden while the library is empty: the empty state already offers a Scan button, and
            // two "Scan" affordances on an otherwise empty screen is one too many.
            if (uiState.hasDocuments) {
                ScanFab(
                    isScanning = uiState.isScanning,
                    listState = listState,
                    onClick = { onAction(HomeIntent.LaunchScanner) },
                )
            }
        },
    ) { padding ->
        AnimatedContent(
            modifier = Modifier.padding(padding).fillMaxSize(),
            targetState = uiState.contentState,
            transitionSpec = { fadeIn(stateFadeSpec) togetherWith fadeOut(stateFadeSpec) },
            label = "HomeContentState",
        ) { state ->
            when (state) {
                HomeContentState.Loading -> DelayedLoading()

                HomeContentState.Error ->
                    HomeStatePanel(
                        icon = Icons.Rounded.Warning,
                        title = stringResource(R.string.couldnt_load_documents),
                        description =
                            uiState.errorMessage ?: stringResource(R.string.error_loading_docs),
                        actionText = stringResource(R.string.retry),
                        onAction = { onAction(HomeIntent.Load) },
                        isError = true,
                        horizontalMargin = horizontalMargin,
                    )

                HomeContentState.EmptyLibrary ->
                    HomeStatePanel(
                        icon = Icons.Rounded.FileCopy,
                        title = stringResource(R.string.no_scanned_documents),
                        description = stringResource(R.string.doc_scan_to_see_document_list),
                        actionText = stringResource(R.string.doc_scan_new),
                        onAction = { onAction(HomeIntent.LaunchScanner) },
                        horizontalMargin = horizontalMargin,
                    )

                HomeContentState.Documents ->
                    DocumentsList(
                        scannedDocuments = uiState.visibleDocuments,
                        filterOptions = uiState.filterOptions,
                        listState = listState,
                        selectedDocumentId = selectedDocumentId,
                        horizontalMargin = horizontalMargin,
                        onAction = onAction,
                    )
            }
        }
    }

    DocumentSearch(
        searchBarState = searchBarState,
        queryState = queryState,
        uiState = uiState,
        horizontalMargin = horizontalMargin,
        selectedDocumentId = selectedDocumentId,
        onAction = onAction,
    )
}

// ─────────────────────────────────────────────
// App bar
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeTopBar(
    uiState: HomeUiState,
    scrollBehavior: TopAppBarScrollBehavior,
    useFlexibleBar: Boolean,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    val title: @Composable () -> Unit = { Text(text = stringResource(R.string.documents)) }

    val actions: @Composable RowScope.() -> Unit = {
        // Only offer search once there is something to search through.
        if (uiState.hasDocuments) {
            IconButton(onClick = onSearch) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = stringResource(R.string.search_documents),
                )
            }
        }
        IconButton(onClick = onSettings) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = stringResource(R.string.settings),
            )
        }
    }

    if (useFlexibleBar) {
        val summary = rememberLibrarySummary(uiState.visibleDocuments)
        val subtitle: (@Composable () -> Unit)? =
            if (summary == null) {
                null
            } else {
                { Text(text = summary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }

        MediumFlexibleTopAppBar(
            title = title,
            subtitle = subtitle,
            actions = actions,
            scrollBehavior = scrollBehavior,
        )
    } else {
        TopAppBar(title = title, actions = actions, scrollBehavior = scrollBehavior)
    }
}

/** "12 documents · 4.2 MB", or null when there is nothing to summarise. */
@Composable
private fun rememberLibrarySummary(documents: List<ScannedDocument>): String? {
    val context = LocalContext.current
    if (documents.isEmpty()) return null

    val count =
        pluralStringResource(
            id = R.plurals.doc_n_documents,
            count = documents.size,
            documents.size,
        )
    val totalBytes = remember(documents) { documents.sumOf { it.fileSize } }

    return stringResource(R.string.library_summary, count, formatFileSize(context, totalBytes))
}

// ─────────────────────────────────────────────
// Primary action
// ─────────────────────────────────────────────

@Composable
private fun ScanFab(isScanning: Boolean, listState: LazyListState, onClick: () -> Unit) {
    // Expanded at rest and at the top of the list; collapsed while the user is scrolling, which
    // gives the content back the width the label was using.
    val expanded by remember {
        derivedStateOf { !listState.isScrollInProgress || !listState.canScrollBackward }
    }

    ExtendedFloatingActionButton(
        text = { Text(text = stringResource(R.string.scan)) },
        expanded = expanded,
        icon = {
            if (isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = LocalContentColor.current,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.DocumentScanner,
                    contentDescription = stringResource(R.string.doc_scan_new),
                )
            }
        },
        onClick = { if (!isScanning) onClick() },
    )
}

// ─────────────────────────────────────────────
// List
// ─────────────────────────────────────────────

@Composable
private fun DocumentsList(
    scannedDocuments: List<ScannedDocument>,
    filterOptions: FilterOptions,
    listState: LazyListState,
    selectedDocumentId: String?,
    horizontalMargin: Dp,
    onAction: (HomeIntent) -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current

    // The max-width cap is applied once, to the whole column, so the sort row and the list stay
    // aligned with each other on large windows.
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxSize()) {
            SortRow(
                currentSortOption = filterOptions.sortBy,
                onSortOptionChange = {
                    onAction(HomeIntent.ApplySort(it))
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
                },
                listState = listState,
                horizontalMargin = horizontalMargin,
            )

            DocumentItems(
                scannedDocuments = scannedDocuments,
                listState = listState,
                selectedDocumentId = selectedDocumentId,
                horizontalMargin = horizontalMargin,
                onAction = onAction,
                bottomSpacer = FabSpacerHeight,
            )
        }
    }
}

@Composable
private fun DocumentItems(
    scannedDocuments: List<ScannedDocument>,
    listState: LazyListState,
    selectedDocumentId: String?,
    horizontalMargin: Dp,
    onAction: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
    bottomSpacer: Dp = 0.dp,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(
            items = scannedDocuments,
            key = { _, scannedDocument -> scannedDocument.uuid },
            contentType = { _, _ -> "scannedDocument" },
        ) { index, scannedDocument ->
            val position =
                when {
                    scannedDocuments.size == 1 -> ScannedDocumentCardPosition.SINGLE
                    index == 0 -> ScannedDocumentCardPosition.TOP
                    index == scannedDocuments.lastIndex -> ScannedDocumentCardPosition.BOTTOM
                    else -> ScannedDocumentCardPosition.MIDDLE
                }

            ScannedDocumentListItem(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(horizontal = horizontalMargin)
                        .animateItem(
                            fadeInSpec = motionScheme.defaultEffectsSpec(),
                            // The visible re-sort is the whole point of the sort control.
                            placementSpec = motionScheme.slowSpatialSpec(),
                            fadeOutSpec = motionScheme.defaultEffectsSpec(),
                        ),
                pdf = scannedDocument,
                position = position,
                selected = scannedDocument.uuid == selectedDocumentId,
                onItemClick = { id -> onAction(HomeIntent.ViewDocument(id)) },
                onItemLongClick = { onAction(HomeIntent.OpenSheet(scannedDocument.uuid)) },
            )
        }

        if (bottomSpacer > 0.dp) {
            item(contentType = "bottomSpacer") {
                Spacer(modifier = Modifier.height(bottomSpacer))
            }
        }
    }
}

// ─────────────────────────────────────────────
// Sort
// ─────────────────────────────────────────────

@Composable
private fun SortRow(
    currentSortOption: SortOption,
    onSortOptionChange: (SortOption) -> Unit,
    listState: LazyListState,
    horizontalMargin: Dp,
    modifier: Modifier = Modifier,
) {
    val isScrolled by remember { derivedStateOf { listState.canScrollBackward } }

    // Opaque, so list content passing underneath can never overlap the labels.
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Column {
            Row(
                modifier =
                    Modifier.fillMaxWidth().padding(horizontal = horizontalMargin, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SelectionGroupRow(
                    options = SortOption.Criteria.entries,
                    selectedOption = currentSortOption.criteria,
                    onOptionSelected = { criteria ->
                        onSortOptionChange(SortOption(criteria, currentSortOption.order))
                    },
                    modifier = Modifier.weight(1f),
                    labelContent = {
                        Text(
                            text = it.getLocalizedName(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )

                IconButton(
                    onClick = {
                        onSortOptionChange(
                            SortOption(
                                currentSortOption.criteria,
                                currentSortOption.order.reverse(),
                            )
                        )
                    }
                ) {
                    Icon(
                        imageVector = currentSortOption.getSortIcon(),
                        contentDescription =
                            if (currentSortOption.order == SortOption.Order.ASC) {
                                stringResource(R.string.sort_ascending)
                            } else {
                                stringResource(R.string.sort_descending)
                            },
                    )
                }
            }

            // Says "there is content above" only when that is actually true.
            AnimatedVisibility(visible = isScrolled, enter = fadeIn(), exit = fadeOut()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

// ─────────────────────────────────────────────
// Search
// ─────────────────────────────────────────────

/**
 * Full-screen search.
 *
 * Search lives here rather than in the FAB slot because it is a mode the user enters deliberately:
 * while active it deserves the whole screen, and while inactive it should cost nothing.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DocumentSearch(
    searchBarState: SearchBarState,
    queryState: androidx.compose.foundation.text.input.TextFieldState,
    uiState: HomeUiState,
    horizontalMargin: Dp,
    selectedDocumentId: String?,
    onAction: (HomeIntent) -> Unit,
) {
    val scope = rememberCoroutineScope()

    val collapse: () -> Unit = {
        scope.launch {
            searchBarState.animateToCollapsed()
            // Leaving search restores the full library rather than silently keeping it filtered.
            queryState.clearText()
        }
    }

    val inputField: @Composable () -> Unit = {
        SearchBarDefaults.InputField(
            textFieldState = queryState,
            searchBarState = searchBarState,
            onSearch = {},
            placeholder = { Text(text = stringResource(R.string.doc_name_or_description)) },
            leadingIcon = {
                IconButton(onClick = collapse) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.exit_search),
                    )
                }
            },
            trailingIcon = {
                if (queryState.text.isNotEmpty()) {
                    IconButton(onClick = { queryState.clearText() }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.clear_search),
                        )
                    }
                }
            },
        )
    }

    ExpandedFullScreenSearchBar(state = searchBarState, inputField = inputField) {
        if (uiState.isEmptyResult) {
            HomeStatePanel(
                icon = Icons.Rounded.SearchOff,
                title = stringResource(R.string.no_matching_documents, uiState.searchQuery),
                description = stringResource(R.string.no_matching_documents_hint),
                actionText = stringResource(R.string.clear_search),
                onAction = { queryState.clearText() },
                horizontalMargin = horizontalMargin,
            )
        } else {
            DocumentItems(
                scannedDocuments = uiState.visibleDocuments,
                listState = rememberLazyListState(),
                selectedDocumentId = selectedDocumentId,
                horizontalMargin = horizontalMargin,
                onAction = { intent ->
                    // Opening a document from search closes search with it.
                    if (intent is HomeIntent.ViewDocument) collapse()
                    onAction(intent)
                },
            )
        }
    }
}

// ─────────────────────────────────────────────
// States
// ─────────────────────────────────────────────

/**
 * Shared layout for the empty, no-results and error screens, so all three read as one design rather
 * than as three separately styled placeholders.
 *
 * Deliberately not wrapped in a card: this fills the screen, and a container boundary drawn around
 * a screen-sized element communicates nothing.
 */
@Composable
private fun HomeStatePanel(
    icon: ImageVector,
    title: String,
    description: String,
    horizontalMargin: Dp,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    isError: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = modifier.fillMaxSize().padding(horizontal = horizontalMargin),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = MaxContentWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                // Error colour is spent on the icon alone; the screen does not turn red.
                color = if (isError) colorScheme.errorContainer else colorScheme.surfaceContainer,
                contentColor =
                    if (isError) colorScheme.onErrorContainer else colorScheme.onSurfaceVariant,
            ) {
                Icon(
                    modifier = Modifier.padding(20.dp).size(40.dp),
                    imageVector = icon,
                    // The text carries the meaning; the icon is decorative.
                    contentDescription = null,
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            if (actionText != null && onAction != null) {
                Button(onClick = onAction) { Text(text = actionText) }
            }
        }
    }
}

/** Shows nothing until the wait is long enough to be worth acknowledging. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DelayedLoading(modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(LOADING_INDICATOR_DELAY_MS)
        visible = true
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (visible) LoadingIndicator()
    }
}

// ─────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────

@PreviewLightDark
@Composable
private fun HomeContentPreview() {
    DocucraftTheme {
        HomeContent(
            uiState =
                HomeUiState(
                    status = HomeStatus.Idle,
                    hasDocuments = true,
                    visibleDocuments = MockData.Documents.documentsList,
                ),
            onAction = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun HomeContentLoadingPreview() {
    DocucraftTheme {
        HomeContent(uiState = HomeUiState(status = HomeStatus.Loading), onAction = {})
    }
}

@PreviewLightDark
@Composable
private fun HomeContentErrorPreview() {
    DocucraftTheme {
        HomeContent(
            uiState = HomeUiState(status = HomeStatus.Error("Local storage is unavailable")),
            onAction = {},
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
        )
    }
}
