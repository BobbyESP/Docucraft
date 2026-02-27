package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.motionScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.components.selectiongroup.SelectionGroupRow
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
import com.bobbyesp.docucraft.core.presentation.theme.dmSerifTextFont
import com.bobbyesp.docucraft.core.presentation.utilities.modifier.customOverscroll
import com.bobbyesp.docucraft.feature.docscanner.domain.FilterOptions
import com.bobbyesp.docucraft.feature.docscanner.domain.SortOption
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.card.ScannedDocumentCardPosition
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.card.ScannedDocumentListItem
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeStatus
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeUiAction
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeUiState
import com.bobbyesp.docucraft.util.MockData
import com.materialkolor.ktx.harmonize
import kotlin.math.roundToInt

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalSharedTransitionApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun HomeContent(
    uiState: HomeUiState,
    onAction: (HomeUiAction) -> Unit,
    onOpenSheet: (documentId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val searchQuery = uiState.searchQuery
    val filterOptions = uiState.filterOptions

    val listState = rememberLazyListState()

    val focusManager = LocalFocusManager.current
    val usableMotionScheme = motionScheme

    Scaffold(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(onTap = { focusManager.clearFocus() })
        },
        topBar = {
            TopAppBar(
                modifier = Modifier,
                title = {
                    Column(
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(id = R.string.app_name),
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            var isSearchFocused by remember { mutableStateOf(false) }

            AnimatedContent(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp)
                    .windowInsetsPadding(WindowInsets.ime),
                targetState = uiState.hasDocuments,
                transitionSpec = {
                    ContentTransform(
                        targetContentEnter = slideInVertically(
                            animationSpec = usableMotionScheme.defaultSpatialSpec()
                        ) + fadeIn(tween(500)),
                        initialContentExit = slideOutVertically(usableMotionScheme.defaultSpatialSpec()) + fadeOut(
                            tween(500)
                        ),
                    )
                },
            ) { isFabAndSearchVisible ->
                if (isFabAndSearchVisible) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SearchBar(
                            query = searchQuery,
                            onQueryChange = { onAction(HomeUiAction.UpdateSearchQuery(it)) },
                            onClear = { onAction(HomeUiAction.ClearSearch) },
                            onFocusChange = { isSearchFocused = it },
                            modifier = Modifier.weight(1f),
                        )

                        ExtendedFloatingActionButton(
                            text = { Text(text = stringResource(id = R.string.scan)) },
                            expanded = !isSearchFocused,
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.DocumentScanner,
                                    contentDescription = stringResource(id = R.string.doc_scan_new),
                                )
                            },
                            onClick = { onAction(HomeUiAction.LaunchDocumentScanner) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        AnimatedContent(
            modifier = Modifier.padding(padding),
            targetState = uiState.status,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
            label = "PageContentTransition",
        ) { targetState ->
            when (targetState) {
                HomeStatus.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularWavyProgressIndicator()
                    }
                }

                is HomeStatus.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ErrorContent(errorMessage = targetState.message, onRetry = { /* TODO */ })
                    }
                }

                HomeStatus.Idle -> {
                    if (!uiState.hasDocuments) {
                        Box(
                            modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                        ) {
                            EmptyStateScreen(
                                modifier = Modifier,
                                onScanDocument = {
                                    onAction(HomeUiAction.LaunchDocumentScanner)
                                }
                            )
                        }
                    } else {
                        ScannedDocumentsList(
                            scannedDocuments = uiState.visibleDocuments,
                            onAction = onAction,
                            onOpenSheet = onOpenSheet,
                            filterOptions = filterOptions,
                            listState = listState,
                        )
                    }
                }

            }
        }
    }
}

@Composable
private fun ScannedDocumentsList(
    scannedDocuments: List<ScannedDocument>,
    filterOptions: FilterOptions,
    onAction: (HomeUiAction) -> Unit,
    onOpenSheet: (documentId: String) -> Unit,
    listState: LazyListState,
) {
    val hapticFeedback = LocalHapticFeedback.current

    var animatedOverscrollAmount by remember { mutableFloatStateOf(0f) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .customOverscroll(
                listState = listState,
                onNewOverscrollAmount = { animatedOverscrollAmount = it },
            )
            .offset { IntOffset(0, animatedOverscrollAmount.roundToInt()) },
        state = listState,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        stickyHeader(contentType = "documentsFilter") {
            SortOptionsRow(
                currentSortOption = filterOptions.sortBy,
                onSortOptionChange = {
                    onAction(HomeUiAction.ApplySort(it))
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
                },
                modifier = Modifier
                    .background(
                        // fade to transparent from background
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background,
                                Color.Transparent,
                            ),
                            startY = 100f,
                        )
                    )
                    .padding(8.dp),
            )
        }

        itemsIndexed(
            items = scannedDocuments,
            key = { _, scannedDocument -> scannedDocument.id },
            contentType = { _, scannedDocument -> scannedDocument.javaClass.name },
        ) { index, scannedDocument ->
            val position = when {
                scannedDocuments.size == 1 -> ScannedDocumentCardPosition.SINGLE
                index == 0 -> ScannedDocumentCardPosition.TOP
                index == scannedDocuments.lastIndex -> ScannedDocumentCardPosition.BOTTOM
                else -> ScannedDocumentCardPosition.MIDDLE
            }

            ScannedDocumentListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .animateItem(),
                pdf = scannedDocument,
                position = position,
                onItemClick = { id -> onAction(HomeUiAction.ViewDocument(id)) },
                onItemLongClick = { onOpenSheet(scannedDocument.id) },
            )
        }

        item(contentType = "bottomBarActionsSpacer") { Spacer(modifier = Modifier.height(88.dp)) }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SortOptionsRow(
    currentSortOption: SortOption,
    onSortOptionChange: (SortOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        SelectionGroupRow(
            options = SortOption.Criteria.entries,
            selectedOption = currentSortOption.criteria,
            onOptionSelected = { criteria ->
                onSortOptionChange(SortOption(criteria, currentSortOption.order))
            },
            modifier = Modifier.weight(1f),
            key = { it.name },
            labelContent = { Text(it.getLocalizedName()) },
        )

        VerticalDivider(
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .height(24.dp)
                .padding(horizontal = 8.dp),
        )

        IconButton(
            modifier = Modifier, colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
            ), onClick = {
                onSortOptionChange(
                    SortOption(currentSortOption.criteria, currentSortOption.order.reverse())
                )
            }) {
            Icon(
                imageVector = currentSortOption.getSortIcon(),
                contentDescription = if (currentSortOption.order == SortOption.Order.ASC) {
                    stringResource(R.string.sort_ascending)
                } else {
                    stringResource(R.string.sort_descending)
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.shadow(4.dp, MaterialTheme.shapes.large),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { onFocusChange(it.isFocused) },
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = MaterialTheme.colorScheme.error,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            placeholder = {
                Text(
                    modifier = Modifier.alpha(0.66f),
                    text = stringResource(R.string.doc_name_or_description),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = stringResource(R.string.search),
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.Rounded.Clear,
                            contentDescription = stringResource(R.string.clear_search),
                        )
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EmptyStateScreen(onScanDocument: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .border(
                width = 2.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = .8f),
                        MaterialTheme.colorScheme.primary.copy(alpha = .2f),
                    )
                ),
                shape = RoundedCornerShape(16.dp),
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Rounded.FileCopy,
                contentDescription = stringResource(R.string.doc_scan_new),
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = stringResource(R.string.no_scanned_documents),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = stringResource(R.string.doc_scan_to_see_document_list),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = onScanDocument,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(56.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = stringResource(R.string.doc_scan_new),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun ErrorContent(errorMessage: String?, onRetry: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .border(
                width = 2.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.error.copy(alpha = .8f),
                        MaterialTheme.colorScheme.error.copy(alpha = .2f),
                    )
                ),
                shape = RoundedCornerShape(16.dp),
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
                .harmonize(other = MaterialTheme.colorScheme.errorContainer)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Rounded.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(64.dp),
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(id = R.string.unknown_error),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = errorMessage ?: stringResource(id = R.string.error_loading_docs),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(50.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = R.string.retry),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun HomeContentPreview() {
    DocucraftTheme {
        HomeContent(
            uiState = HomeUiState(visibleDocuments = MockData.Documents.documentsList),
            onAction = {},
            onOpenSheet = {},
        )
    }
}
