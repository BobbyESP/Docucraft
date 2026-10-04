/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.bin

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.components.ScreenPlaceholderCard
import com.bobbyesp.docucraft.core.presentation.components.divider.AnimatedWavyDivider
import com.bobbyesp.docucraft.core.presentation.components.divider.defaults.AnimatedWavyDividerDefaults
import com.bobbyesp.docucraft.core.presentation.components.others.GridMenu
import com.bobbyesp.docucraft.core.presentation.components.others.GridMenuItem
import com.bobbyesp.docucraft.core.presentation.components.overlay.OverlayForm
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftShapeDefaults
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
import com.bobbyesp.docucraft.core.util.animateItemWith
import com.bobbyesp.docucraft.core.util.contentRevealTransform
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.BinRetention
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.card.ScannedDocumentListItem
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.list.FrostedLargeTopAppBar
import com.bobbyesp.docucraft.feature.docscanner.presentation.preview.DocumentPreviewData
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

private enum class BinPage {
    Loading,
    Empty,
    Documents,
}

/**
 * The bin: what was deleted, each saying how long it still has. A document here is not opened; it
 * is brought back or deleted for good, so tapping one offers those two.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BinScreen(
    uiState: BinUiState,
    onBack: () -> Unit,
    onOpenDocumentActions: (String) -> Unit,
    onEmptyBin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val motionScheme = MaterialTheme.motionScheme
    val layoutDirection = LocalLayoutDirection.current
    val hazeState = rememberHazeState()

    val page =
        when {
            uiState.isLoading -> BinPage.Loading
            uiState.isEmpty -> BinPage.Empty
            else -> BinPage.Documents
        }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            FrostedLargeTopAppBar(
                title = stringResource(R.string.bin),
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
                actions = {
                    if (page == BinPage.Documents) {
                        TextButton(
                            onClick = onEmptyBin,
                            shapes = ButtonDefaults.shapes(),
                            colors =
                                ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.padding(end = ButtonDefaults.IconSpacing),
                            )
                            Text(text = stringResource(R.string.bin_empty))
                        }
                    }
                },
            )
        },
    ) { padding ->
        AnimatedContent(
            modifier = Modifier.fillMaxSize().hazeSource(hazeState),
            targetState = page,
            transitionSpec = { motionScheme.contentRevealTransform() },
            label = "BinPage",
        ) { targetPage ->
            val centered = Modifier.fillMaxSize().padding(padding)
            when (targetPage) {
                BinPage.Loading ->
                    Box(modifier = centered, contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }

                BinPage.Empty ->
                    Box(modifier = centered, contentAlignment = Alignment.Center) {
                        ScreenPlaceholderCard(
                            modifier = Modifier.padding(24.dp),
                            title = stringResource(R.string.bin_nothing_title),
                            description =
                                stringResource(R.string.bin_nothing_desc, BinRetention.DAYS),
                            icon = Icons.Rounded.DeleteOutline,
                        )
                    }

                BinPage.Documents ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding =
                            PaddingValues(
                                start = padding.calculateStartPadding(layoutDirection),
                                top = padding.calculateTopPadding() + 8.dp,
                                end = padding.calculateEndPadding(layoutDirection),
                                bottom = padding.calculateBottomPadding() + 16.dp,
                            ),
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                    ) {
                        item(key = "retention", contentType = "notice") {
                            RetentionNotice(
                                modifier =
                                    Modifier.padding(horizontal = 16.dp).padding(bottom = 14.dp)
                            )
                        }
                        itemsIndexed(
                            items = uiState.documents,
                            key = { _, binned -> binned.document.uuid },
                            contentType = { _, _ -> "document" },
                        ) { index, binned ->
                            val openActions = { onOpenDocumentActions(binned.document.uuid) }
                            ScannedDocumentListItem(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .then(animateItemWith(motionScheme)),
                                pdf = binned.document,
                                shapes =
                                    DocucraftShapeDefaults.segmentedListItemShapes(
                                        index = index,
                                        count = uiState.documents.size,
                                    ),
                                note = daysLeftLabel(binned.daysLeft),
                                onItemClick = { openActions() },
                                onItemLongClick = openActions,
                            )
                        }
                    }
            }
        }
    }
}

/** How long a document still has in the bin, in words. */
@Composable
private fun daysLeftLabel(daysLeft: Int): String =
    if (daysLeft <= 0) stringResource(R.string.bin_deleted_soon)
    else pluralStringResource(R.plurals.bin_days_left, daysLeft, daysLeft)

/** What the bin does on its own, said once at its top rather than on every document. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RetentionNotice(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = DocucraftShapeDefaults.cardShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(imageVector = Icons.Rounded.AutoDelete, contentDescription = null)
            Text(
                text = stringResource(R.string.bin_retention_notice, BinRetention.DAYS),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * What can be done to a document of the bin, under what it is: the sheet a document of the library
 * has, with the two things that apply here.
 *
 * @param stacked whether there is room to put the header above the actions rather than beside them.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BinDocumentActionsContent(
    binned: BinnedDocument,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    modifier: Modifier = Modifier,
    stacked: Boolean = true,
) {
    val header: @Composable (Modifier) -> Unit = { headerModifier ->
        Column(
            modifier = headerModifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = binned.document.name,
                style = MaterialTheme.typography.titleLargeEmphasized,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = daysLeftLabel(binned.daysLeft),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    val actions: @Composable () -> Unit = {
        GridMenu(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            content = {
                GridMenuItem(
                    icon = Icons.Rounded.RestoreFromTrash,
                    title = R.string.restore,
                    containerColor = { MaterialTheme.colorScheme.primary },
                    span = { GridItemSpan(maxLineSpan) },
                    onClick = onRestore,
                )
                GridMenuItem(
                    icon = Icons.Rounded.DeleteForever,
                    title = R.string.delete_forever,
                    containerColor = { MaterialTheme.colorScheme.error },
                    span = { GridItemSpan(maxLineSpan) },
                    onClick = onDeleteForever,
                )
            },
        )
    }

    if (stacked) {
        Column(modifier = modifier) {
            header(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            AnimatedWavyDivider(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                strokeWidth = 4.dp,
                colors =
                    AnimatedWavyDividerDefaults.colors(
                        color = MaterialTheme.colorScheme.outlineVariant
                    ),
            )
            Box(modifier = Modifier.heightIn(min = 120.dp)) { actions() }
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            header(Modifier.weight(1f).padding(start = 16.dp))
            Box(modifier = Modifier.weight(1f).heightIn(min = 120.dp)) { actions() }
        }
    }
}

/** The last word before a document is gone: this one cannot be taken back. */
@Composable
fun DeleteForeverForm(
    binned: BinnedDocument,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OverlayForm(
        title = stringResource(R.string.delete_forever),
        icon = Icons.Rounded.DeleteForever,
        onDismiss = onDismiss,
        modifier = modifier,
        confirmText = stringResource(R.string.delete),
        onConfirm = onConfirm,
        destructive = true,
    ) {
        PermanentDeletionWarning(
            text = stringResource(R.string.delete_forever_confirmation, binned.document.name)
        )
    }
}

@Composable
fun EmptyBinForm(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OverlayForm(
        title = stringResource(R.string.bin_empty),
        icon = Icons.Rounded.DeleteSweep,
        onDismiss = onDismiss,
        modifier = modifier,
        confirmText = stringResource(R.string.bin_empty),
        onConfirm = onConfirm,
        destructive = true,
    ) {
        PermanentDeletionWarning(
            text = pluralStringResource(R.plurals.bin_empty_confirmation, count, count)
        )
    }
}

@Composable
private fun PermanentDeletionWarning(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = text, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Text(
            text = stringResource(R.string.delete_forever_note),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@PreviewLightDark
@Composable
private fun BinScreenPreview() {
    DocucraftTheme {
        BinScreen(
            uiState =
                BinUiState(
                    documents =
                        DocumentPreviewData.documents.mapIndexed { index, document ->
                            BinnedDocument(document, daysLeft = 30 - index * 29)
                        },
                    isLoading = false,
                ),
            onBack = {},
            onOpenDocumentActions = {},
            onEmptyBin = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun BinScreenEmptyPreview() {
    DocucraftTheme {
        BinScreen(
            uiState = BinUiState(isLoading = false),
            onBack = {},
            onOpenDocumentActions = {},
            onEmptyBin = {},
        )
    }
}
