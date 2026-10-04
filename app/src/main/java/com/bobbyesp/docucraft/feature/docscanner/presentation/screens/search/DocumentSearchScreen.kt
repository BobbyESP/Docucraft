/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.components.ScreenPlaceholderCard
import com.bobbyesp.docucraft.core.presentation.navigation.motion.isDestinationSettled
import com.bobbyesp.docucraft.core.presentation.navigation.motion.sharedBoundsAcrossDestinations
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftBlurDefaults
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftShapeDefaults
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
import com.bobbyesp.docucraft.core.presentation.theme.blurHalo
import com.bobbyesp.docucraft.core.presentation.theme.frosted
import com.bobbyesp.docucraft.core.util.animateItemWith
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SearchResult
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.card.ScannedDocumentListItem
import com.bobbyesp.docucraft.feature.docscanner.presentation.preview.DocumentPreviewData
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.koin.androidx.compose.koinViewModel

/**
 * Both ends of the search bar's morph: Home's button and this screen's field. One key and one
 * shape, so the container that travels between them is recognisably the same one.
 */
private object SearchBarElement {
    const val KEY = "document-search-bar"
    val Shape = CircleShape
    val Height = 56.dp

    /** Frosted on both ends too, so the container that travels does not change material halfway. */
    @Composable
    @ReadOnlyComposable
    fun frostedStyle(): HazeBlurStyle =
        DocucraftBlurDefaults.surfaceStyle(MaterialTheme.colorScheme.surfaceContainerHigh)
}

/**
 * Home's way into search: looks like the search bar it becomes, and is not one. Typing happens on
 * the search screen, which this grows into.
 *
 * Kept at the bottom beside the scan button, where the thumb already is, and floating over the list
 * like it. Frosted rather than shadowed: the documents scrolling beneath show through it, blurred.
 * What lifts it is the blur halo its caller draws around it and the scan button together; where
 * there is none, the shadow it used to have.
 *
 * @param hazeState Where the content it floats over is recorded.
 */
@Composable
fun DocumentSearchBarButton(
    onClick: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier =
            modifier
                .sharedBoundsAcrossDestinations(SearchBarElement.KEY, SearchBarElement.Shape)
                .height(SearchBarElement.Height)
                .frosted(
                    state = hazeState,
                    style = SearchBarElement.frostedStyle(),
                    shape = SearchBarElement.Shape,
                ),
        shape = SearchBarElement.Shape,
        color = Color.Transparent,
        shadowElevation = if (DocucraftBlurDefaults.isHaloSupported) 0.dp else 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = Icons.Rounded.Search, contentDescription = null)
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.search_documents),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun DocumentSearchScreen(
    onBack: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onOpenDocumentActions: (String) -> Unit,
    modifier: Modifier = Modifier,
    selectedDocumentId: String? = null,
    viewModel: DocumentSearchViewModel = koinViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()

    DocumentSearchContent(
        uiState = uiState,
        onQueryChange = { viewModel.onSendIntent(DocumentSearchIntent.UpdateQuery(it)) },
        onClearQuery = { viewModel.onSendIntent(DocumentSearchIntent.ClearQuery) },
        onBack = onBack,
        onOpenDocument = onOpenDocument,
        onOpenDocumentActions = onOpenDocumentActions,
        modifier = modifier,
        selectedDocumentId = selectedDocumentId,
    )
}

/**
 * Results above, the field at the bottom: the field stays where Home's button was, just above the
 * keyboard, so the thumb that opened search types in it without reaching.
 *
 * The field floats, frosted, over the results rather than below them, as Home's button floats over
 * the list: they scroll beneath it, and the room it takes is added to their padding.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DocumentSearchContent(
    uiState: DocumentSearchUiState,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onBack: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onOpenDocumentActions: (String) -> Unit,
    modifier: Modifier = Modifier,
    selectedDocumentId: String? = null,
) {
    val motionScheme = MaterialTheme.motionScheme
    val density = LocalDensity.current
    val hazeState = rememberHazeState()

    // The field's height with the keyboard and its margins: everything the results must clear.
    var fieldHeight by remember { mutableStateOf(0.dp) }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                    )
        ) {
            AnimatedContent(
                targetState =
                    when {
                        uiState.query.isBlank() -> SearchPage.Hint
                        uiState.hasNoMatches -> SearchPage.NoMatches
                        else -> SearchPage.Results
                    },
                modifier = Modifier.fillMaxSize().hazeSource(hazeState),
                transitionSpec = {
                    fadeIn(motionScheme.defaultEffectsSpec()) togetherWith
                        fadeOut(motionScheme.fastEffectsSpec())
                },
                label = "SearchPage",
            ) { page ->
                when (page) {
                    SearchPage.Hint -> SearchHint(Modifier.padding(bottom = fieldHeight))
                    SearchPage.NoMatches ->
                        Box(
                            modifier = Modifier.fillMaxSize().padding(bottom = fieldHeight),
                            contentAlignment = Alignment.Center,
                        ) {
                            ScreenPlaceholderCard(
                                modifier = Modifier.padding(24.dp),
                                title = stringResource(R.string.doc_no_matches),
                                description =
                                    stringResource(R.string.doc_no_matches_desc, uiState.query),
                                icon = Icons.Rounded.SearchOff,
                            )
                        }
                    SearchPage.Results ->
                        SearchResults(
                            results = uiState.results,
                            notFoundUuids = uiState.notFoundUuids,
                            selectedDocumentId = selectedDocumentId,
                            onOpenDocument = onOpenDocument,
                            onOpenDocumentActions = onOpenDocumentActions,
                            bottomClearance = fieldHeight,
                        )
                }
            }

            SearchField(
                query = uiState.query,
                onQueryChange = onQueryChange,
                onClearQuery = onClearQuery,
                onBack = onBack,
                hazeState = hazeState,
                modifier =
                    Modifier.align(Alignment.BottomCenter)
                        .onSizeChanged { fieldHeight = with(density) { it.height.toDp() } }
                        .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                        .padding(16.dp),
            )
        }
    }
}

private enum class SearchPage {
    Hint,
    NoMatches,
    Results,
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onBack: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val motionScheme = MaterialTheme.motionScheme
    // The frost is the container; the field's own stays clear so it does not cover it.
    val containerColor = Color.Transparent

    // The keyboard comes up once the bar has landed, not while it is still growing: both at once
    // made the field chase its own target. Only the first time, too: coming back from a document
    // to the results is not asking to type again.
    val settled = isDestinationSettled()
    var hasFocusedOnce by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(settled) {
        if (settled && !hasFocusedOnce) {
            focusRequester.requestFocus()
            hasFocusedOnce = true
        }
    }

    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier =
            modifier
                .fillMaxWidth()
                // Outside the shared bounds: the halo stays behind with this screen while the
                // field flies back to Home, instead of being cut to the field's shape in flight.
                .blurHalo(state = hazeState, shape = SearchBarElement.Shape)
                .sharedBoundsAcrossDestinations(SearchBarElement.KEY, SearchBarElement.Shape)
                .frosted(
                    state = hazeState,
                    style = SearchBarElement.frostedStyle(),
                    shape = SearchBarElement.Shape,
                )
                .focusRequester(focusRequester),
        placeholder = {
            Text(
                text = stringResource(R.string.search_documents),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }
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
                IconButton(onClick = onClearQuery, shapes = IconButtonDefaults.shapes()) {
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
        shape = SearchBarElement.Shape,
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchResults(
    results: List<SearchResult>,
    notFoundUuids: Set<String>,
    selectedDocumentId: String?,
    onOpenDocument: (String) -> Unit,
    onOpenDocumentActions: (String) -> Unit,
    bottomClearance: Dp,
) {
    val motionScheme = MaterialTheme.motionScheme
    val statusBar = WindowInsets.statusBars.asPaddingValues()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = 16.dp,
                top = statusBar.calculateTopPadding() + 16.dp,
                end = 16.dp,
                bottom = bottomClearance,
            ),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        itemsIndexed(
            items = results,
            key = { _, result -> result.document.uuid },
            contentType = { _, _ -> "document" },
        ) { index, result ->
            val document = result.document
            ScannedDocumentListItem(
                modifier = Modifier.fillMaxWidth().then(animateItemWith(motionScheme)),
                pdf = document,
                passage = result.passage,
                shapes =
                    DocucraftShapeDefaults.segmentedListItemShapes(
                        index = index,
                        count = results.size,
                    ),
                selected = document.uuid == selectedDocumentId,
                fileMissing = document.uuid in notFoundUuids,
                onItemClick = onOpenDocument,
                onItemLongClick = { onOpenDocumentActions(document.uuid) },
            )
        }
    }
}

/** What an empty query is for, in place of a list of everything, which Home already shows. */
@Composable
private fun SearchHint(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.search_hint_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.search_hint_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@PreviewLightDark
@Composable
private fun DocumentSearchResultsPreview() {
    DocucraftTheme {
        DocumentSearchContent(
            uiState =
                DocumentSearchUiState(
                    query = "doc",
                    results =
                        DocumentPreviewData.documents.map { SearchResult(it, passage = null) },
                    resultsFor = "doc",
                ),
            onQueryChange = {},
            onClearQuery = {},
            onBack = {},
            onOpenDocument = {},
            onOpenDocumentActions = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun DocumentSearchHintPreview() {
    DocucraftTheme {
        DocumentSearchContent(
            uiState = DocumentSearchUiState(),
            onQueryChange = {},
            onClearQuery = {},
            onBack = {},
            onOpenDocument = {},
            onOpenDocumentActions = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun DocumentSearchBarButtonPreview() {
    DocucraftTheme {
        DocumentSearchBarButton(
            onClick = {},
            hazeState = rememberHazeState(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
