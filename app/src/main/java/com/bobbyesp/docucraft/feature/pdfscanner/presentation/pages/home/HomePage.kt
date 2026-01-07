package com.bobbyesp.docucraft.feature.pdfscanner.presentation.pages.home

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
import com.bobbyesp.docucraft.core.presentation.theme.dmSerifTextFont
import com.bobbyesp.docucraft.core.presentation.utilities.modifier.customOverscroll
import com.bobbyesp.docucraft.feature.pdfscanner.domain.FilterOptions
import com.bobbyesp.docucraft.feature.pdfscanner.domain.SortOption
import com.bobbyesp.docucraft.feature.pdfscanner.domain.model.ScannedPdf
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.components.card.ScannedPdfListItem
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.components.card.ScannedPdfCardPosition
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.components.others.selectiongroup.SelectionGroupItem
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.components.others.selectiongroup.SelectionGroupRow
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.pages.home.viewmodel.HomeUiAction
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.pages.home.viewmodel.HomeUiState
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.pages.home.viewmodel.PageContentState
import com.materialkolor.ktx.harmonize
import kotlin.math.roundToInt

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme.motionScheme

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalSharedTransitionApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun HomePage(uiState: HomeUiState, onAction: (HomeUiAction) -> Unit, onScanClick: () -> Unit) {
    val scannedPdfs = uiState.scannedPdfs
    val filteredPdfs = uiState.filteredPdfs
    val searchQuery = uiState.searchQuery
    val filterOptions = uiState.filterOptions

    val listState = rememberLazyListState()
    val isFabExpanded by remember { derivedStateOf { !listState.isScrollInProgress } }

    val visiblePdfs by
    remember(scannedPdfs, filteredPdfs, searchQuery, filterOptions) {
        derivedStateOf {
            if (searchQuery.isNotBlank() || filterOptions != FilterOptions.default) filteredPdfs
            else scannedPdfs
        }
    }

    val focusManager = LocalFocusManager.current
    val usableMotionScheme = motionScheme

    Scaffold(
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures(onTap = {
                focusManager.clearFocus()
            })
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
                            fontFamily = dmSerifTextFont,
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
                targetState = uiState.contentState == PageContentState.SUCCESS,
                transitionSpec = {
                    ContentTransform(
                        targetContentEnter = slideInVertically(
                            animationSpec = usableMotionScheme.defaultSpatialSpec()
                        ) + fadeIn(tween(500)),
                        initialContentExit = slideOutVertically(
                            usableMotionScheme.defaultSpatialSpec()
                        ) + fadeOut(tween(500)),
                    )
                },
            ) { isFabAndSearchVisible ->
                when (isFabAndSearchVisible) {
                    true -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SearchBar(
                                query = searchQuery,
                                onQueryChange = {
                                    onAction(HomeUiAction.UpdateSearchQuery(it))
                                },
                                onClear = { onAction(HomeUiAction.ClearSearch) },
                                onFocusChange = { isSearchFocused = it },
                                modifier = Modifier.weight(1f),
                            )

                            ExtendedFloatingActionButton(
                                text = { Text(text = stringResource(id = R.string.scan)) },
                                expanded = isFabExpanded && !isSearchFocused,
                                icon = {
                                    Icon(
                                        imageVector = Icons.Rounded.DocumentScanner,
                                        contentDescription = stringResource(id = R.string.scan_new_document),
                                    )
                                },
                                onClick = onScanClick,
                            )
                        }
                    }

                    false -> {}
                }
            }
        },
    ) { padding ->
        AnimatedContent(
            modifier = Modifier.padding(padding),
            targetState = uiState.contentState,
            transitionSpec = {
                fadeIn(tween(300)) togetherWith fadeOut(tween(300))
            },
            label = "PageContentTransition",
        ) { targetState ->
            when (targetState) {
                PageContentState.LOADING -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularWavyProgressIndicator()
                    }
                }

                PageContentState.ERROR -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ErrorContent(
                            errorMessage = uiState.errorMessage,
                            onRetry = { onAction(HomeUiAction.LoadPdfs) },
                        )
                    }
                }

                PageContentState.EMPTY -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyStateScreen(modifier = Modifier, onScanPdfClick = onScanClick)
                    }
                }

                PageContentState.SUCCESS -> {
                    DisplayScannedPdfs(
                        scannedPdfs = visiblePdfs,
                        onAction = onAction,
                        filterOptions = filterOptions,
                        listState = listState,
                    )
                }
            }
        }
    }
}

@Composable
private fun DisplayScannedPdfs(
    scannedPdfs: List<ScannedPdf>,
    filterOptions: FilterOptions,
    onAction: (HomeUiAction) -> Unit,
    listState: LazyListState,
) {
    var animatedOverscrollAmount by remember { mutableFloatStateOf(0f) }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .customOverscroll(
                    listState = listState,
                    onNewOverscrollAmount = { animatedOverscrollAmount = it },
                )
                .offset { IntOffset(0, animatedOverscrollAmount.roundToInt()) },
        state = listState,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        stickyHeader {
            SortOptionsRow(
                currentSortOption = filterOptions.sortBy,
                onSortOptionChanged = { onAction(HomeUiAction.ApplySort(it)) },
                modifier =
                    Modifier
                        .background(
                            // fade to transparent from background
                            brush =
                                Brush.verticalGradient(
                                    colors =
                                        listOf(
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
            items = scannedPdfs,
            key = { _, scannedPdf -> scannedPdf.id },
            contentType = { _, scannedPdf -> scannedPdf.javaClass.name },
        ) { index, scannedPdf ->
            val position =
                when {
                    scannedPdfs.size == 1 -> ScannedPdfCardPosition.SINGLE
                    index == 0 -> ScannedPdfCardPosition.TOP
                    index == scannedPdfs.lastIndex -> ScannedPdfCardPosition.BOTTOM
                    else -> ScannedPdfCardPosition.MIDDLE
                }

            ScannedPdfListItem(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .animateItem(),
                pdf = scannedPdf,
                position = position,
                onOpenPdf = { uri -> onAction(HomeUiAction.OpenPdf(uri)) },
                onSharePdf = { uri -> onAction(HomeUiAction.SharePdf(uri)) },
                onDeletePdf = { id -> onAction(HomeUiAction.ShowDeleteConfirmation(id)) },
                onSavePdf = { onAction(HomeUiAction.SavePdf(scannedPdf)) },
                onModifyPdfFields = { id -> onAction(HomeUiAction.ShowEditDialog(id)) },
            )
        }

        item {
            Spacer(modifier = Modifier.height(88.dp))
        }
    }
}

@Composable
fun SortOptionsRow(
    currentSortOption: SortOption,
    onSortOptionChanged: (SortOption) -> Unit,
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
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
        ) {
            SortOption.Criteria.entries.forEach { criteria ->
                SelectionGroupItem(
                    selected = currentSortOption.criteria == criteria,
                    onClick = {
                        onSortOptionChanged(
                            SortOption(
                                criteria,
                                currentSortOption.order
                            )
                        )
                    },
                ) {
                    Text(criteria.getLocalizedName())
                }
            }
        }

        VerticalDivider(
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .height(24.dp)
                .padding(horizontal = 8.dp),
        )

        IconButton(
            onClick = {
                val newOrder =
                    if (currentSortOption.order == SortOption.Order.ASC) {
                        SortOption.Order.DESC
                    } else {
                        SortOption.Order.ASC
                    }
                onSortOptionChanged(SortOption(currentSortOption.criteria, newOrder))
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
                    modifier = modifier.alpha(0.66f),
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
fun EmptyStateScreen(modifier: Modifier = Modifier, onScanPdfClick: () -> Unit) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(
                    width = 2.dp,
                    brush =
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = .8f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = .2f),
                                )
                        ),
                    shape = RoundedCornerShape(16.dp),
                ),
        colors =
            CardDefaults.cardColors(
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
                contentDescription = stringResource(R.string.scan_new_document),
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
                text = stringResource(R.string.scan_documents_to_see_list),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = onScanPdfClick,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(56.dp),
                shape = MaterialTheme.shapes.medium,
                colors =
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = stringResource(R.string.scan_new_document),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun ErrorContent(errorMessage: String?, onRetry: () -> Unit) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(
                    width = 2.dp,
                    brush =
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    MaterialTheme.colorScheme.error.copy(alpha = .8f),
                                    MaterialTheme.colorScheme.error.copy(alpha = .2f),
                                )
                        ),
                    shape = RoundedCornerShape(16.dp),
                ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    MaterialTheme.colorScheme
                        .surfaceColorAtElevation(6.dp)
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
                    text = errorMessage ?: stringResource(id = R.string.error_loading_pdfs),
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
                colors =
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
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

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun PreviewHomePage() {
    DocucraftTheme {
        HomePage(
            uiState =
                HomeUiState(
                    scannedPdfs =
                        listOf(
                            ScannedPdf(
                                id = "1",
                                filename = "document1.pdf",
                                title = "Documento 1 de prueba. Título corto",
                                description =
                                    "Description para el documento 1. La descripción no va a ser muy larga.",
                                path = "content://com.example.documents/document/1".toUri(),
                                createdTimestamp = System.currentTimeMillis(),
                                fileSize = 1024,
                                pageCount = 10,
                                thumbnail = "content://com.example.thumbnails/thumbnail/1",
                            ),
                            ScannedPdf(
                                id = "2",
                                filename = "document2.pdf",
                                title = "Apuntes de programación",
                                description =
                                    "Esta descripción va a sobrepasar el límite de caracteres para ver cómo se comporta el diseño. " +
                                            "Esto es una prueba para ver cómo se comporta el diseño en caso de que la descripción sea muy larga.",
                                path = "content://com.example.documents/document/2".toUri(),
                                createdTimestamp = System.currentTimeMillis(),
                                fileSize = 2048,
                                pageCount = 20,
                                thumbnail = "content://com.example.thumbnails/thumbnail/2",
                            ),
                        )
                ),
            onAction = {},
            onScanClick = {},
        )
    }
}
