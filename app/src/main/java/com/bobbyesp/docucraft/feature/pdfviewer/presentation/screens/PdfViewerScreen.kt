/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.screens

import android.content.ClipData
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.domain.model.ViewerDisplaySettings
import com.bobbyesp.docucraft.core.domain.model.ViewerFitMode
import com.bobbyesp.docucraft.core.domain.notifications.InAppNotification
import com.bobbyesp.docucraft.core.domain.notifications.NotificationType
import com.bobbyesp.docucraft.core.presentation.common.LocalNotificationsService
import com.bobbyesp.docucraft.core.util.events.UiEvent
import com.bobbyesp.docucraft.feature.pdfviewer.domain.actions.DocumentPrinter
import com.bobbyesp.docucraft.feature.pdfviewer.domain.model.ViewerDocumentRef
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.PdfViewerViewModel
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.PageIndicatorPill
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.PdfFastScroller
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.ViewerErrorContent
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.ViewerLoadError
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.rememberViewerChromeState
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.selection.TextSelectionLayer
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.selection.rememberSelectionDrag
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.toolbar.PdfViewerBottomToolbar
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.toolbar.PdfViewerTopBar
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.contract.PdfViewerEffect
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.contract.PdfViewerIntent
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.pages.ViewerPageRequests
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.selection.LongPressOutcome
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.selection.SelectionInteraction
import com.bobbyesp.docucraft.feature.shared.domain.BasicDocument
import com.bobbyesp.documentcontent.NormalizedPoint
import com.composepdf.FitMode
import com.composepdf.PdfInteractionHandler
import com.composepdf.PdfLayoutSpec
import com.composepdf.PdfSource
import com.composepdf.PdfTapEvent
import com.composepdf.PdfViewer
import com.composepdf.PdfViewerDefaults
import com.composepdf.PdfZoomSpec
import com.composepdf.ScrollDirection
import com.composepdf.rememberPdfViewerState
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class,
    FlowPreview::class,
)
@Composable
fun PdfViewerScreen(
    viewModel: PdfViewerViewModel,
    document: ViewerDocumentRef,
    documentInfo: BasicDocument,
    onBack: () -> Unit,
    onOpenDetails: () -> Unit,
    onGoToPage: (currentPage: Int, pageCount: Int) -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Callers only show the screen once these are known; the factory values are a formality.
    val display = state.display ?: ViewerDisplaySettings.Factory
    val pdfViewerState = rememberPdfViewerState()
    val scope = rememberCoroutineScope()

    // A page asked for by *Go to page*, which cannot reach this state itself. Taken once scrolled
    // to.
    val pageRequests: ViewerPageRequests = koinInject()
    LaunchedEffect(document, pdfViewerState) {
        pageRequests.observe(document).filterNotNull().collect { page ->
            if (pageRequests.consume(document, page)) pdfViewerState.animateScrollToPage(page)
        }
    }

    HandlePdfViewerEffects(viewModel)

    val chrome = rememberViewerChromeState()

    // The bars' own measured heights, kept while they are hidden. Handed to the viewer as constant
    // content padding: pages start below the top bar and end above the bottom one, and scroll
    // underneath them. It used to be an animated padding on the viewer itself, which resized the
    // viewport, and relaid the document out, on every frame the bars moved (V9).
    val density = LocalDensity.current
    var topBarHeight by remember { mutableStateOf(0.dp) }
    var bottomBarHeight by remember { mutableStateOf(0.dp) }

    // ---------------------------------------------------------------- text selection

    // The text of the pages on screen is read as they arrive; a fling only reads where it stops.
    LaunchedEffect(pdfViewerState) {
        snapshotFlow { pdfViewerState.visiblePages }
            .distinctUntilChanged()
            .debounce(VisiblePagesSettle)
            .collect { viewModel.onSendIntent(PdfViewerIntent.VisiblePagesChanged(it)) }
    }

    val selectionDrag = rememberSelectionDrag(pdfViewerState)
    val haptics = LocalHapticFeedback.current
    val latestPageText by rememberUpdatedState(state.pageText)
    val selectionHandler =
        remember(selectionDrag, viewModel, haptics) {
            object : PdfInteractionHandler {
                // Decided at once, from the text already read: a long press cannot wait.
                override fun onLongPress(event: PdfTapEvent): Boolean {
                    val page = event.pageIndex ?: return false
                    val at = event.pagePosition ?: return false
                    return when (
                        val outcome =
                            SelectionInteraction.longPress(
                                latestPageText,
                                page,
                                NormalizedPoint(at.x, at.y),
                            )
                    ) {
                        is LongPressOutcome.Select -> {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.onSendIntent(PdfViewerIntent.Select(outcome.selection))
                            selectionDrag.start(
                                anchor = outcome.selection.start,
                                current = outcome.selection,
                                at = event.position,
                            )
                            true
                        }
                        is LongPressOutcome.NoText -> {
                            haptics.performHapticFeedback(HapticFeedbackType.Reject)
                            viewModel.onSendIntent(PdfViewerIntent.NothingToSelect(outcome.reason))
                            false
                        }
                        LongPressOutcome.NoWord,
                        LongPressOutcome.NotReady -> false
                    }
                }

                override fun onDrag(event: PdfTapEvent) = selectionDrag.moveTo(event.position)

                override fun onDragEnd() = selectionDrag.end()
            }
        }

    // Back lets go of the selection before it leaves the viewer.
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = state.selection != null,
        onBackCompleted = { viewModel.onSendIntent(PdfViewerIntent.ClearSelection) },
    )

    // A document that failed cannot be tapped to bring the bars back, and they hold the way out.
    LaunchedEffect(pdfViewerState.error) { if (pdfViewerState.error != null) chrome.show() }

    // A file this app cannot reach cannot be handed to another one either.
    val loadError = pdfViewerState.error?.let(ViewerLoadError::of)
    val canHandOff = state.canHandOff && loadError?.worthOpeningElsewhere != false
    val openWith: (() -> Unit)? =
        if (canHandOff) ({ viewModel.onSendIntent(PdfViewerIntent.OpenWith) }) else null

    Box(modifier = modifier.fillMaxSize().nestedScroll(chrome.nestedScrollConnection)) {
        PdfViewer(
            source = PdfSource.Uri(documentInfo.uri.toUri()),
            state = pdfViewerState,
            layout =
                PdfLayoutSpec(
                    scrollDirection = ScrollDirection.VERTICAL,
                    fitMode = display.fitMode.toEngine(),
                    contentPadding = PaddingValues(top = topBarHeight, bottom = bottomBarHeight),
                ),
            zoomSpec = PdfZoomSpec(minZoom = 0.25f, maxZoom = 10f),
            // The fast scroller below replaces the engine's passive indicator.
            style = PdfViewerDefaults.style(nightMode = display.nightMode, scrollIndicator = null),
            loadingContent = { LoadingIndicator(modifier = Modifier.align(Alignment.Center)) },
            errorContent = { error ->
                ViewerErrorContent(
                    error = loadError ?: ViewerLoadError.of(error),
                    onOpenWith = openWith,
                    onBack = if (showBackButton) onBack else null,
                    modifier =
                        Modifier.align(Alignment.Center)
                            .padding(top = topBarHeight)
                            .verticalScroll(rememberScrollState()),
                )
            },
            // A tap lets go of a selection; otherwise it shows or hides the bars.
            onTap = {
                if (state.selection != null) {
                    viewModel.onSendIntent(PdfViewerIntent.ClearSelection)
                } else {
                    chrome.toggle()
                }
            },
            interactionHandler = selectionHandler,
            overlay = {
                TextSelectionLayer(
                    state = pdfViewerState,
                    pages = state.pageText,
                    selection = state.selection,
                    drag = selectionDrag,
                    contentTop = topBarHeight,
                    contentBottomInset = bottomBarHeight,
                    nightMode = display.nightMode,
                    onSelect = { viewModel.onSendIntent(PdfViewerIntent.Select(it)) },
                    onCopy = { viewModel.onSendIntent(PdfViewerIntent.CopySelection) },
                    onSelectAll = { viewModel.onSendIntent(PdfViewerIntent.SelectAll) },
                )
            },
            modifier = Modifier.fillMaxSize(),
        )

        PdfFastScroller(
            state = pdfViewerState,
            contentTop = topBarHeight,
            modifier =
                Modifier.align(Alignment.TopEnd)
                    .padding(top = topBarHeight, bottom = bottomBarHeight),
        )

        PageIndicatorPill(
            currentPage = pdfViewerState.currentPage,
            pageCount = pdfViewerState.pageCount,
            barsVisible = chrome.isVisible,
            modifier =
                Modifier.align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 8.dp),
        )

        AnimatedVisibility(
            modifier =
                Modifier.align(Alignment.TopCenter).onSizeChanged {
                    if (it.height > 0) topBarHeight = with(density) { it.height.toDp() }
                },
            visible = chrome.isVisible,
            enter =
                fadeIn(animationSpec = MaterialTheme.motionScheme.slowEffectsSpec()) +
                    slideInVertically(
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                        initialOffsetY = { -it },
                    ),
            exit =
                fadeOut(animationSpec = MaterialTheme.motionScheme.slowEffectsSpec()) +
                    slideOutVertically(
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                        targetOffsetY = { -it },
                    ),
        ) {
            PdfViewerTopBar(
                title = documentInfo.title ?: documentInfo.filename,
                description = documentInfo.description,
                showBackButton = showBackButton,
                onBack = onBack,
                onShare =
                    if (canHandOff) ({ viewModel.onSendIntent(PdfViewerIntent.Share) }) else null,
                onPrint =
                    if (pdfViewerState.isLoaded) ({ viewModel.onSendIntent(PdfViewerIntent.Print) })
                    else null,
                onOpenWith = openWith,
                onDetails = onOpenDetails,
            )
        }

        AnimatedVisibility(
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .onSizeChanged {
                        if (it.height > 0) bottomBarHeight = with(density) { it.height.toDp() }
                    }
                    .padding(
                        bottom =
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                                16.dp
                    ),
            // Nothing to page through or zoom in a document that failed. Kept while loading: its
            // height is the content padding, and learning it after the document is laid out would
            // move a restored position.
            visible = chrome.isVisible && pdfViewerState.error == null,
            enter =
                fadeIn(animationSpec = MaterialTheme.motionScheme.slowEffectsSpec()) +
                    slideInVertically(
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                        initialOffsetY = { it },
                    ),
            exit =
                fadeOut(animationSpec = MaterialTheme.motionScheme.slowEffectsSpec()) +
                    slideOutVertically(
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                        targetOffsetY = { it },
                    ),
        ) {
            PdfViewerBottomToolbar(
                currentPage = pdfViewerState.currentPage,
                pageCount = pdfViewerState.pageCount,
                zoom = pdfViewerState.zoom,
                canZoomIn = pdfViewerState.zoom < pdfViewerState.maxZoom,
                canZoomOut = pdfViewerState.zoom > pdfViewerState.minZoom,
                fitMode = display.fitMode,
                nightMode = display.nightMode,
                onPageClick = {
                    onGoToPage(pdfViewerState.currentPage, pdfViewerState.pageCount)
                },
                onZoomIn = {
                    scope.launch {
                        pdfViewerState.animateZoomTo(pdfViewerState.zoom * ZoomStep)
                    }
                },
                onZoomOut = {
                    scope.launch {
                        pdfViewerState.animateZoomTo(pdfViewerState.zoom / ZoomStep)
                    }
                },
                onResetZoom = { scope.launch { pdfViewerState.animateResetZoom() } },
                onFitModeChange = { viewModel.onSendIntent(PdfViewerIntent.SetFitMode(it)) },
                onNightModeToggle = { viewModel.onSendIntent(PdfViewerIntent.ToggleNightMode) },
            )
        }
    }
}

/** Carries out what the ViewModel cannot, for want of an activity, and shows what it has to say. */
@Composable
private fun HandlePdfViewerEffects(viewModel: PdfViewerViewModel) {
    val activity = requireNotNull(LocalActivity.current) { "The PDF viewer needs an activity" }
    val printer: DocumentPrinter = koinInject { parametersOf(activity) }
    val notifications = LocalNotificationsService.current
    val clipboard = LocalClipboard.current
    val copied = stringResource(R.string.viewer_text_copied)

    LaunchedEffect(viewModel, printer) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is PdfViewerEffect.Print -> printer.print(effect.document, effect.jobName)
                is PdfViewerEffect.CopyText -> {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(copied, effect.text)))
                    // From Android 13 the system confirms a copy itself.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        notifications.show(
                            InAppNotification(message = copied, type = NotificationType.Success)
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.defaultEvents.collectLatest { event ->
            when (event) {
                is UiEvent.ShowMessage ->
                    notifications.show(
                        InAppNotification(message = event.message, type = event.type)
                    )
            }
        }
    }
}

private fun ViewerFitMode.toEngine(): FitMode =
    when (this) {
        ViewerFitMode.WIDTH -> FitMode.WIDTH
        ViewerFitMode.HEIGHT -> FitMode.HEIGHT
        ViewerFitMode.BOTH -> FitMode.BOTH
        ViewerFitMode.PROPORTIONAL -> FitMode.PROPORTIONAL
    }

/** How long the pages on screen must stay put before their text is read. */
private val VisiblePagesSettle = 150.milliseconds

/** Each zoom button press scales by this much, animated. */
private const val ZoomStep = 1.25f
