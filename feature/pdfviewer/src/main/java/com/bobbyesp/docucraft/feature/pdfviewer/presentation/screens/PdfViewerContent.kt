/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.screens

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.bobbyesp.docucraft.core.designsystem.theme.DocucraftTheme
import com.bobbyesp.docucraft.core.domain.model.ViewerDisplaySettings
import com.bobbyesp.docucraft.core.domain.model.ViewerFitMode
import com.bobbyesp.docucraft.feature.pdfviewer.R
import com.bobbyesp.docucraft.feature.pdfviewer.domain.links.BlockReason
import com.bobbyesp.docucraft.feature.pdfviewer.domain.links.LinkAction
import com.bobbyesp.docucraft.feature.pdfviewer.domain.search.MatchRef
import com.bobbyesp.docucraft.feature.pdfviewer.domain.search.SearchMatches
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.PageIndicatorPill
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.PdfFastScroller
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.ViewerBar
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.ViewerBarEdge
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.ViewerErrorContent
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.ViewerLoadError
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.links.LinkLayer
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.search.FindBar
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.search.FindToolbar
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.search.SearchHighlightLayer
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.search.ViewerSearchState
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.selection.TextSelectionLayer
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.selection.rememberViewerInteractionHandler
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.toolbar.PdfViewerBottomToolbar
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.toolbar.PdfViewerTopBar
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.contract.PdfViewerIntent
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.contract.PdfViewerUiState
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.contract.ViewerDocumentState
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.contract.ViewerStart
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.search.DocumentSearch
import com.bobbyesp.docucraft.library.domain.model.BasicDocument
import com.bobbyesp.documentcontent.PageLink
import com.bobbyesp.documentcontent.TextSpan
import com.composepdf.FitMode
import com.composepdf.PdfLayoutSpec
import com.composepdf.PdfSource
import com.composepdf.PdfViewer
import com.composepdf.PdfViewerDefaults
import com.composepdf.PdfZoomSpec
import com.composepdf.ScrollDirection
import dev.chrisbanes.haze.hazeSource

/**
 * The pages, with the bars, the search and the fast scroller floating over them.
 *
 * @param resolveLink What following a link would do, to describe it to TalkBack.
 */
@Composable
internal fun PdfViewerContent(
    uiState: PdfViewerUiState,
    documentInfo: BasicDocument,
    screenState: PdfViewerScreenState,
    resolveLink: (PageLink) -> LinkAction,
    showBackButton: Boolean,
    onBack: () -> Unit,
    onOpenDetails: () -> Unit,
    onGoToPage: (currentPage: Int, pageCount: Int) -> Unit,
    onIntent: (PdfViewerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pdf = screenState.pdf
    val search = screenState.search
    val bars = screenState.bars
    val hazeState = screenState.hazeState
    val display = uiState.display ?: ViewerDisplaySettings.Factory
    val contentPadding =
        with(LocalDensity.current) {
            PaddingValues(top = bars.topPx.toDp(), bottom = bars.bottomPx.toDp())
        }

    val loadError = screenState.loadError
    // A file this app cannot reach cannot be handed to another one either.
    val canHandOff = uiState.canHandOff && loadError?.worthOpeningElsewhere != false
    val onOpenWith: (() -> Unit)? =
        if (canHandOff) ({ onIntent(PdfViewerIntent.OpenWith) }) else null
    val barsVisible = screenState.barsVisible

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .onSizeChanged(bars::onViewerMeasured)
                .nestedScroll(screenState.chrome.nestedScrollConnection)
    ) {
        // The engine does not take a new state under a viewer already laid out: one per location.
        key(documentInfo.uri) {
            ViewerPages(
                documentUri = documentInfo.uri,
                uiState = uiState,
                display = display,
                screenState = screenState,
                contentPadding = contentPadding,
                loadError = loadError,
                resolveLink = resolveLink,
                onOpenWith = onOpenWith,
                onBack = if (showBackButton) onBack else null,
                onIntent = onIntent,
                modifier = Modifier.fillMaxSize().hazeSource(hazeState),
            )
        }

        PdfFastScroller(
            state = pdf,
            contentTop = contentPadding.calculateTopPadding(),
            modifier = Modifier.align(Alignment.TopEnd).padding(contentPadding),
        )

        PageIndicatorPill(
            currentPage = pdf.currentPage,
            pageCount = pdf.pageCount,
            barsVisible = barsVisible,
            modifier =
                Modifier.align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 8.dp),
        )

        ViewerBar(
            visible = barsVisible && !search.isOpen,
            edge = ViewerBarEdge.Top,
            onHeightMeasured = bars::onTopBarMeasured,
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            PdfViewerTopBar(
                title = documentInfo.title ?: documentInfo.filename,
                description = documentInfo.description,
                showBackButton = showBackButton,
                onBack = onBack,
                // Not before the document is on screen: what cannot be shown is not kept.
                onSaveToLibrary =
                    if (uiState.canSaveToLibrary && pdf.isLoaded) {
                        { onIntent(PdfViewerIntent.SaveToLibrary) }
                    } else null,
                isSavingToLibrary = uiState.isSavingToLibrary,
                onShare = if (canHandOff) ({ onIntent(PdfViewerIntent.Share) }) else null,
                onPrint = if (pdf.isLoaded) ({ onIntent(PdfViewerIntent.Print) }) else null,
                onOpenWith = onOpenWith,
                onDetails = onOpenDetails,
                onSearch =
                    if (pdf.isLoaded) {
                        {
                            // One focus at a time: a selection or a preview is let go of.
                            onIntent(PdfViewerIntent.ClearSelection)
                            onIntent(PdfViewerIntent.DismissLinkPreview)
                            search.open()
                        }
                    } else null,
                hazeState = hazeState,
            )
        }

        // In the top bar's place and not measured, so the pages, padded by the top bar's height,
        // do not move when a search opens.
        ViewerBar(
            visible = search.isOpen,
            edge = ViewerBarEdge.Top,
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            FindBar(
                field = search.field,
                focusRequests = search.focusRequests,
                onClose = screenState::closeSearch,
                onStep = { onIntent(PdfViewerIntent.SearchStep(it)) },
                hazeState = hazeState,
            )
        }

        ViewerBar(
            // Kept while loading: its height is the pages' padding, and learning it after the
            // layout would move a restored position.
            visible = barsVisible && !search.isOpen && pdf.error == null,
            edge = ViewerBarEdge.Bottom,
            onHeightMeasured = bars::onBottomBarMeasured,
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                    .padding(bottom = FloatingToolbarDefaults.ScreenOffset),
        ) {
            PdfViewerBottomToolbar(
                currentPage = pdf.currentPage,
                pageCount = pdf.pageCount,
                zoom = pdf.zoom,
                canZoomIn = pdf.zoom < pdf.maxZoom,
                canZoomOut = pdf.zoom > pdf.minZoom,
                fitMode = display.fitMode,
                nightMode = display.nightMode,
                onPageClick = { onGoToPage(pdf.currentPage, pdf.pageCount) },
                onZoomIn = screenState::zoomIn,
                onZoomOut = screenState::zoomOut,
                onResetZoom = screenState::resetZoom,
                onFitModeChange = { onIntent(PdfViewerIntent.SetFitMode(it)) },
                onNightModeToggle = { onIntent(PdfViewerIntent.ToggleNightMode) },
                hazeState = hazeState,
            )
        }

        val activeSearch = uiState.search?.takeIf { it.isActive }
        // The last one, so the toolbar leaves with its numbers when the search ends.
        val shownSearch = rememberLastNonNull(activeSearch)
        ViewerBar(
            visible = search.isOpen && activeSearch != null,
            edge = ViewerBarEdge.Bottom,
            onHeightMeasured = bars::onFindToolbarMeasured,
            // Rises with the keyboard, so the arrows stay within reach while typing.
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                    .padding(bottom = FloatingToolbarDefaults.ScreenOffset),
        ) {
            shownSearch?.let {
                FindToolbar(
                    search = it,
                    onStep = { forward -> onIntent(PdfViewerIntent.SearchStep(forward)) },
                    hazeState = hazeState,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ViewerPages(
    documentUri: String,
    uiState: PdfViewerUiState,
    display: ViewerDisplaySettings,
    screenState: PdfViewerScreenState,
    contentPadding: PaddingValues,
    loadError: ViewerLoadError?,
    resolveLink: (PageLink) -> LinkAction,
    onOpenWith: (() -> Unit)?,
    onBack: (() -> Unit)?,
    onIntent: (PdfViewerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pdf = screenState.pdf
    val interactionHandler =
        rememberViewerInteractionHandler(uiState, pdf, screenState.selectionDrag, onIntent)

    PdfViewer(
        source = PdfSource.Uri(documentUri.toUri()),
        state = pdf,
        layout =
            PdfLayoutSpec(
                scrollDirection = ScrollDirection.VERTICAL,
                fitMode = display.fitMode.toEngine(),
                contentPadding = contentPadding,
            ),
        zoomSpec = PdfZoomSpec(minZoom = 0.25f, maxZoom = 10f),
        // The fast scroller takes the place of the engine's indicator.
        style = PdfViewerDefaults.style(nightMode = display.nightMode, scrollIndicator = null),
        loadingContent = { LoadingIndicator(modifier = Modifier.align(Alignment.Center)) },
        errorContent = { error ->
            ViewerErrorContent(
                error = loadError ?: ViewerLoadError.of(error),
                onOpenWith = onOpenWith,
                onBack = onBack,
                modifier =
                    Modifier.align(Alignment.Center)
                        .padding(top = contentPadding.calculateTopPadding())
                        .verticalScroll(rememberScrollState()),
            )
        },
        onTap = {
            when {
                uiState.linkPreview != null -> onIntent(PdfViewerIntent.DismissLinkPreview)
                uiState.selection != null -> onIntent(PdfViewerIntent.ClearSelection)
                screenState.search.isOpen -> screenState.dismissKeyboard()
                else -> screenState.chrome.toggle()
            }
        },
        interactionHandler = interactionHandler,
        overlay = {
            // Bottom to top: what the reader chose, or can press, stays above what was found.
            SearchHighlightLayer(
                search = uiState.search,
                pages = uiState.pageText,
                nightMode = display.nightMode,
            )
            TextSelectionLayer(
                state = pdf,
                pages = uiState.pageText,
                selection = uiState.selection,
                drag = screenState.selectionDrag,
                contentTop = contentPadding.calculateTopPadding(),
                contentBottomInset = contentPadding.calculateBottomPadding(),
                nightMode = display.nightMode,
                onSelect = { onIntent(PdfViewerIntent.Select(it)) },
                onCopy = { onIntent(PdfViewerIntent.CopySelection) },
                onSelectAll = { onIntent(PdfViewerIntent.SelectAll) },
            )
            LinkLayer(
                pageLinks = uiState.pageLinks,
                preview = uiState.linkPreview,
                describe = { linkDescription(resolveLink(it)) },
                onTapLink = { page, link ->
                    onIntent(PdfViewerIntent.TapLink(page, link, pdf.pageCount))
                },
                onOpen = { onIntent(PdfViewerIntent.OpenPreviewedLink) },
                onCopy = { onIntent(PdfViewerIntent.CopyPreviewedLink) },
            )
        },
        modifier = modifier,
    )
}

/** [value], or the last one that was not `null`: what an exit animation goes on showing. */
@Composable
private fun <T : Any> rememberLastNonNull(value: T?): T? {
    val last = remember { LastValue<T>() }
    if (value != null) last.value = value
    return last.value
}

private class LastValue<T : Any> {
    var value: T? = null
}

/** What TalkBack says for a link: where it leads. */
@Composable
private fun linkDescription(action: LinkAction): String =
    when (action) {
        is LinkAction.OpenWeb -> stringResource(R.string.link_a11y_web, action.host)
        is LinkAction.ComposeEmail -> stringResource(R.string.link_a11y_email, action.address)
        is LinkAction.Dial -> stringResource(R.string.link_a11y_phone, action.number)
        is LinkAction.GoTo -> stringResource(R.string.link_a11y_page, action.pageIndex + 1)
        is LinkAction.Blocked -> stringResource(R.string.link_a11y_blocked)
    }

private fun ViewerFitMode.toEngine(): FitMode =
    when (this) {
        ViewerFitMode.WIDTH -> FitMode.WIDTH
        ViewerFitMode.HEIGHT -> FitMode.HEIGHT
        ViewerFitMode.BOTH -> FitMode.BOTH
        ViewerFitMode.PROPORTIONAL -> FitMode.PROPORTIONAL
    }

// The engine only reads the file once composed, so a preview shows the bars around the loading
// indicator.
private val PreviewDocument =
    BasicDocument(
        uuid = "preview",
        filename = "invoice-march.pdf",
        uri = "content://preview/invoice-march.pdf",
        title = "Invoice March",
        description = "Paid on 12/03",
    )

private fun previewUiState(search: DocumentSearch? = null) =
    PdfViewerUiState(
        document = ViewerDocumentState.Open(PreviewDocument),
        display = ViewerDisplaySettings.Factory,
        start = ViewerStart(position = null),
        search = search,
    )

@Composable
private fun PdfViewerContentPreview(uiState: PdfViewerUiState, query: String? = null) {
    val search = remember { ViewerSearchState(isOpen = query != null, query = query.orEmpty()) }
    val screenState =
        rememberPdfViewerScreenState(
            documentUri = PreviewDocument.uri,
            start = null,
            search = search,
        )
    DocucraftTheme {
        Surface {
            PdfViewerContent(
                uiState = uiState,
                documentInfo = PreviewDocument,
                screenState = screenState,
                resolveLink = { LinkAction.Blocked(target = "", reason = BlockReason.Scheme) },
                showBackButton = true,
                onBack = {},
                onOpenDetails = {},
                onGoToPage = { _, _ -> },
                onIntent = {},
            )
        }
    }
}

@Preview(name = "Phone", device = Devices.PHONE)
@Preview(
    name = "Phone, dark",
    device = Devices.PHONE,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Preview(name = "Tablet", device = Devices.TABLET)
@Composable
private fun PdfViewerContentReadingPreview() {
    PdfViewerContentPreview(uiState = previewUiState())
}

@Preview(name = "Searching", device = Devices.PHONE)
@Composable
private fun PdfViewerContentSearchingPreview() {
    PdfViewerContentPreview(
        uiState =
            previewUiState(
                search =
                    DocumentSearch(
                        query = "invoice",
                        matches =
                            SearchMatches.None.with(0, listOf(TextSpan(0, 7), TextSpan(20, 27))),
                        active = MatchRef(0, 1),
                        activeNumber = 2,
                        scannedPages = 4,
                        pageCount = 12,
                    )
            ),
        query = "invoice",
    )
}
