package com.bobbyesp.docucraft.feature.pdfscanner.presentation.pages.home

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
import com.bobbyesp.docucraft.core.presentation.utilities.modifier.customOverscroll
import com.bobbyesp.docucraft.feature.pdfscanner.domain.FilterOptions
import com.bobbyesp.docucraft.feature.pdfscanner.domain.SortOption
import com.bobbyesp.docucraft.feature.pdfscanner.domain.model.ScannedPdf
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.components.card.ScannedPdfCard
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.components.others.selectiongroup.SelectionGroupItem
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.components.others.selectiongroup.SelectionGroupRow
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.pages.home.HomeViewModel.Event.NotifyUserAction.OpenPdfFieldsDialog
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.pages.home.HomeViewModel.Event.NotifyUserAction.WarnAboutDeletion
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.pages.home.HomeViewModel.Event.PdfAction.Open
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.pages.home.HomeViewModel.Event.PdfAction.Save
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.pages.home.HomeViewModel.Event.PdfAction.Share
import com.materialkolor.ktx.harmonize
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun HomePage(
    scannedPdfs: List<ScannedPdf>,
    filteredPdfs: List<ScannedPdf>,
    searchQuery: String,
    filterOptions: FilterOptions,
    loadingState: HomeViewModel.LoadingState,
    onEvent: (HomeViewModel.Event) -> Unit,
) {
    val activity = LocalActivity.current
    val thereIsNoPdfs by remember(scannedPdfs) {
        derivedStateOf { scannedPdfs.isEmpty() }
    }

    val pdfsToShow by remember(scannedPdfs, filteredPdfs, searchQuery, filterOptions) {
        derivedStateOf {
            if (searchQuery.isNotBlank() || filterOptions != FilterOptions()) filteredPdfs else scannedPdfs
        }
    }

    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        onEvent(HomeViewModel.Event.HandlePdfScanningResult(result))
    }

    SharedTransitionLayout(
        modifier = Modifier.fillMaxSize(),
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    modifier = Modifier,
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(id = R.string.app_name).uppercase(),
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.titleLarge,
                                letterSpacing = 4.sp,
                            )
                        }
                    },
                )
            },
            floatingActionButton = {
                AnimatedContent(
                    targetState = thereIsNoPdfs,
                    transitionSpec = {
                        ContentTransform(
                            targetContentEnter = expandIn() + fadeIn(),
                            initialContentExit = shrinkOut() + slideOutVertically(),
                        )
                    },
                ) { showFab ->
                    if (!showFab) {
                        ExtendedFloatingActionButton(
                            text = { Text(text = stringResource(id = R.string.scan)) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.DocumentScanner,
                                    contentDescription = stringResource(id = R.string.scan_new_document),
                                )
                            },
                            onClick = {
                                onEvent(
                                    HomeViewModel.Event.ScanPdf(
                                        activity = activity, listener = scannerLauncher
                                    )
                                )
                            },
                        )
                    }
                }
            },
        ) { padding ->
            Crossfade(
                modifier = Modifier.padding(padding), targetState = loadingState
            ) { state ->
                when (state) {
                    is HomeViewModel.LoadingState.Error -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            ErrorContent(
                                errorMessage = state.message,
                                onRetry = { onEvent(HomeViewModel.Event.ReloadPdfs) })
                        }
                    }

                    HomeViewModel.LoadingState.Idle -> {
                        Crossfade(thereIsNoPdfs) { showEmptyState ->
                            if (showEmptyState) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    EmptyStateScreen(
                                        modifier = Modifier, onScanPdfClick = {
                                            onEvent(
                                                HomeViewModel.Event.ScanPdf(
                                                    activity = activity, listener = scannerLauncher
                                                )
                                            )
                                        })
                                }
                            } else {
                                DisplayScannedPdfs(
                                    scannedPdfs = pdfsToShow,
                                    onEvent = onEvent,
                                    filterOptions = filterOptions,
                                )
                            }
                        }
                    }

                    HomeViewModel.LoadingState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DisplayScannedPdfs(
    scannedPdfs: List<ScannedPdf>,
    filterOptions: FilterOptions,
    onEvent: (HomeViewModel.Event) -> Unit,
) {
    val lazyListState = rememberLazyListState()
    var animatedOverscrollAmount by remember { mutableFloatStateOf(0f) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .customOverscroll(
                listState = lazyListState,
                onNewOverscrollAmount = { animatedOverscrollAmount = it },
            )
            .offset { IntOffset(0, animatedOverscrollAmount.roundToInt()) },
        state = lazyListState,
    ) {
        stickyHeader {
            SortOptionsRow(
                currentSortOption = filterOptions.sortBy,
                onSortOptionChanged = { onEvent(HomeViewModel.Event.SearchFilterEvent.ApplySort(it)) },
                modifier = Modifier.padding(16.dp)
            )
        }

        items(
            items = scannedPdfs,
            key = { scannedPdf -> scannedPdf.id },
            contentType = { scannedPdf -> scannedPdf.javaClass.name },
        ) { scannedPdf ->
            ScannedPdfCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem(),
                pdf = scannedPdf,
                onOpenPdf = { uri -> onEvent(Open(uri)) },
                onSharePdf = { uri -> onEvent(Share(uri)) },
                onDeletePdf = { uri -> onEvent(WarnAboutDeletion(uri)) },
                onSavePdf = { onEvent(Save(scannedPdf)) },
                onModifyPdfFields = { id -> onEvent(OpenPdfFieldsDialog(id)) },
            )
        }
    }
}

@Composable
fun SortOptionsRow(
    currentSortOption: SortOption,
    onSortOptionChanged: (SortOption) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SelectionGroupRow(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
        ) {
            SortOption.Criteria.entries.forEach { criteria ->
                SelectionGroupItem(
                    selected = currentSortOption.criteria == criteria, onClick = {
                        onSortOptionChanged(SortOption(criteria, currentSortOption.order))
                    }) {
                    Text(criteria.getLocalizedName())
                }
            }
        }

        VerticalDivider(
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .height(24.dp)
                .padding(horizontal = 8.dp)
        )

        IconButton(
            onClick = {
                val newOrder = if (currentSortOption.order == SortOption.Order.ASC) {
                    SortOption.Order.DESC
                } else {
                    SortOption.Order.ASC
                }
                onSortOptionChanged(SortOption(currentSortOption.criteria, newOrder))
            }) {
            Icon(
                imageVector = currentSortOption.getSortIcon(),
                contentDescription = if (currentSortOption.order == SortOption.Order.ASC) {
                    stringResource(R.string.sort_ascending)
                } else {
                    stringResource(R.string.sort_descending)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text(stringResource(R.string.search_documents)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = stringResource(R.string.search)
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Rounded.Clear,
                        contentDescription = stringResource(R.string.clear_search)
                    )
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.large
    )
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EmptyStateScreen(
    modifier: Modifier = Modifier, onScanPdfClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .border(
                width = 2.dp, brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = .8f),
                        MaterialTheme.colorScheme.primary.copy(alpha = .2f),
                    )
                ), shape = RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.FileCopy,
                contentDescription = stringResource(R.string.scan_new_document),
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = stringResource(R.string.no_scanned_documents),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = stringResource(R.string.scan_documents_to_see_list),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onScanPdfClick,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(56.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                elevation = ButtonDefaults.elevatedButtonElevation(
                    defaultElevation = 8.dp
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = stringResource(R.string.scan_new_document),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun ErrorContent(
    errorMessage: String?, onRetry: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .border(
                width = 2.dp, brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.error.copy(alpha = .8f),
                        MaterialTheme.colorScheme.error.copy(alpha = .2f),
                    )
                ), shape = RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
                .harmonize(other = MaterialTheme.colorScheme.errorContainer)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(64.dp)
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.unknown_error),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
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
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                elevation = ButtonDefaults.elevatedButtonElevation(
                    defaultElevation = 8.dp
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = R.string.retry),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Preview
@Composable
private fun PreviewMidas() {
    DocucraftTheme {
        HomePage(
            scannedPdfs = emptyList(),
            loadingState = HomeViewModel.LoadingState.Error(
                IllegalStateException("Error"), "Error"
            ),
            onEvent = {},
            filteredPdfs = emptyList(),
            searchQuery = "",
            filterOptions = FilterOptions()
        )
    }
}