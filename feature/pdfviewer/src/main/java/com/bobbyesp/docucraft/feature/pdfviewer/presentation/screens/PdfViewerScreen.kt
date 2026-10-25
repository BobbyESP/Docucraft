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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.bobbyesp.docucraft.core.designsystem.R as DesignsystemR
import com.bobbyesp.docucraft.core.domain.model.ViewerDisplaySettings
import com.bobbyesp.docucraft.core.domain.model.ViewerFitMode
import com.bobbyesp.docucraft.core.domain.notifications.InAppNotification
import com.bobbyesp.docucraft.core.domain.notifications.NotificationAction
import com.bobbyesp.docucraft.core.domain.notifications.NotificationType
import com.bobbyesp.docucraft.core.ui.common.LocalNotificationsService
import com.bobbyesp.docucraft.core.ui.events.UiEvent
import com.bobbyesp.docucraft.feature.pdfviewer.R
import com.bobbyesp.docucraft.feature.pdfviewer.domain.actions.DocumentPrinter
import com.bobbyesp.docucraft.feature.pdfviewer.domain.links.LinkAction
import com.bobbyesp.docucraft.feature.pdfviewer.domain.links.LinkLook
import com.bobbyesp.docucraft.feature.pdfviewer.domain.links.LinkOpener
import com.bobbyesp.docucraft.feature.pdfviewer.domain.links.ResolveLinkUseCase
import com.bobbyesp.docucraft.feature.pdfviewer.domain.model.ViewerDocumentRef
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.PdfViewerViewModel
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.PageIndicatorPill
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.PdfFastScroller
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.ViewerErrorContent
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.ViewerLoadError
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.links.LinkLayer
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.rememberViewerChromeState
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.selection.SelectionDrag
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.selection.TextSelectionLayer
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.selection.rememberSelectionDrag
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.toolbar.PdfViewerBottomToolbar
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.toolbar.PdfViewerTopBar
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.contract.PdfViewerEffect
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.contract.PdfViewerIntent
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.contract.PdfViewerUiState
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.pages.ViewerPageRequests
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.selection.LongPressOutcome
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.selection.SelectionInteraction
import com.bobbyesp.docucraft.library.domain.model.BasicDocument
import com.bobbyesp.docucraft.library.domain.model.ReadingPosition
import com.bobbyesp.documentcontent.NormalizedPoint
import com.bobbyesp.documentcontent.PageLink
import com.composepdf.FitMode
import com.composepdf.PdfInteractionHandler
import com.composepdf.PdfLayoutSpec
import com.composepdf.PdfReadingPosition
import com.composepdf.PdfSource
import com.composepdf.PdfTapEvent
import com.composepdf.PdfViewer
import com.composepdf.PdfViewerDefaults
import com.composepdf.PdfViewerState
import com.composepdf.PdfZoomSpec
import com.composepdf.ScrollDirection
import com.composepdf.rememberPdfViewerState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PdfViewerScreen(
    viewModel: PdfViewerViewModel,
    document: ViewerDocumentRef,
    documentInfo: BasicDocument,
    onBack: () -> Unit,
    onOpenDetails: () -> Unit,
    onGoToPage: (currentPage: Int, pageCount: Int) -> Unit,
    onConfirmSaveCopy: (documentUuid: String) -> Unit,
    onOpenInLibrary: (documentUuid: String) -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Callers only show the screen once these are known; the factory values are a formality.
    val display = state.display ?: ViewerDisplaySettings.Factory
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val onIntent = viewModel::onSendIntent

    // Where the reader was last seen, whichever file the document was read from. It wins over
    // `state.start`: after a rotation or a process death it is the later of the two.
    val seen = remember { LastSeen() }

    // Keyed by where the document is read from: saving another app's document into the library
    // moves it, and a state of its own starts the new place where the reader was, not at the top.
    val pdfViewerState =
        key(documentInfo.uri) {
            rememberPdfViewerState(
                initialPosition = seen.position ?: state.start?.position?.toEngine()
            )
        }

    val chrome = rememberViewerChromeState()
    val bars = remember { BarHeights() }
    // The document, recorded for the bars to frost. They are its siblings, so none blurs itself.
    val hazeState = rememberHazeState()
    val selectionDrag = rememberSelectionDrag(pdfViewerState)
    val selectionHandler = rememberSelectionHandler(state, pdfViewerState, selectionDrag, onIntent)
    val resolveLink: ResolveLinkUseCase = koinInject()

    ReportReadingPosition(pdfViewerState, seen) {
        onIntent(PdfViewerIntent.ReadingPositionChanged(it))
    }
    ConsumePageRequests(document, pdfViewerState)
    ReadVisiblePages(pdfViewerState, onIntent)
    DismissLinkPreviewOnMove(pdfViewerState, previewShown = state.linkPreview != null, onIntent)
    HandlePdfViewerEffects(
        viewModel = viewModel,
        pdfViewerState = pdfViewerState,
        contentTop = { bars.topPx(density) },
        onConfirmSaveCopy = onConfirmSaveCopy,
        onOpenInLibrary = onOpenInLibrary,
    )

    // Back closes a link's preview, then lets go of the selection, before it leaves the viewer.
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = state.linkPreview != null || state.selection != null,
        onBackCompleted = {
            onIntent(
                if (state.linkPreview != null) PdfViewerIntent.DismissLinkPreview
                else PdfViewerIntent.ClearSelection
            )
        },
    )

    // A failed document cannot be tapped to bring the bars back, and they hold the way out.
    LaunchedEffect(pdfViewerState.error) { if (pdfViewerState.error != null) chrome.show() }

    val loadError = pdfViewerState.error?.let(ViewerLoadError::of)
    // A file this app cannot reach cannot be handed to another one either.
    val canHandOff = state.canHandOff && loadError?.worthOpeningElsewhere != false
    val openWith: (() -> Unit)? = if (canHandOff) ({ onIntent(PdfViewerIntent.OpenWith) }) else null

    // Whether the file was there is only known once the engine has tried to read it.
    LaunchedEffect(pdfViewerState.isLoaded, loadError) {
        when {
            loadError != null -> onIntent(PdfViewerIntent.DocumentFailedToLoad(loadError))
            pdfViewerState.isLoaded ->
                onIntent(PdfViewerIntent.DocumentLoaded(pdfViewerState.pageCount))
        }
    }

    Box(modifier = modifier.fillMaxSize().nestedScroll(chrome.nestedScrollConnection)) {
        // A viewer for each place the document is read from, like its state: the engine does not
        // take a new state under a viewer that is already laid out.
        key(documentInfo.uri) {
            PdfViewer(
                source = PdfSource.Uri(documentInfo.uri.toUri()),
                state = pdfViewerState,
                layout =
                    PdfLayoutSpec(
                        scrollDirection = ScrollDirection.VERTICAL,
                        fitMode = display.fitMode.toEngine(),
                        contentPadding = PaddingValues(top = bars.top, bottom = bars.bottom),
                    ),
                zoomSpec = PdfZoomSpec(minZoom = 0.25f, maxZoom = 10f),
                // The fast scroller replaces the engine's passive indicator.
                style =
                    PdfViewerDefaults.style(nightMode = display.nightMode, scrollIndicator = null),
                loadingContent = { LoadingIndicator(modifier = Modifier.align(Alignment.Center)) },
                errorContent = { error ->
                    ViewerErrorContent(
                        error = loadError ?: ViewerLoadError.of(error),
                        onOpenWith = openWith,
                        onBack = if (showBackButton) onBack else null,
                        modifier =
                            Modifier.align(Alignment.Center)
                                .padding(top = bars.top)
                                .verticalScroll(rememberScrollState()),
                    )
                },
                // Closes a link's preview, else lets go of a selection, else toggles the bars.
                onTap = {
                    when {
                        state.linkPreview != null -> onIntent(PdfViewerIntent.DismissLinkPreview)
                        state.selection != null -> onIntent(PdfViewerIntent.ClearSelection)
                        else -> chrome.toggle()
                    }
                },
                interactionHandler = selectionHandler,
                overlay = {
                    TextSelectionLayer(
                        state = pdfViewerState,
                        pages = state.pageText,
                        selection = state.selection,
                        drag = selectionDrag,
                        contentTop = bars.top,
                        contentBottomInset = bars.bottom,
                        nightMode = display.nightMode,
                        onSelect = { onIntent(PdfViewerIntent.Select(it)) },
                        onCopy = { onIntent(PdfViewerIntent.CopySelection) },
                        onSelectAll = { onIntent(PdfViewerIntent.SelectAll) },
                    )
                    LinkLayer(
                        pageLinks = state.pageLinks,
                        preview = state.linkPreview,
                        describe = { describeLink(it, resolveLink, pdfViewerState.pageCount) },
                        onTapLink = { page, link ->
                            onIntent(PdfViewerIntent.TapLink(page, link, pdfViewerState.pageCount))
                        },
                        onOpen = { onIntent(PdfViewerIntent.OpenPreviewedLink) },
                        onCopy = { onIntent(PdfViewerIntent.CopyPreviewedLink) },
                    )
                },
                modifier = Modifier.fillMaxSize().hazeSource(hazeState),
            )
        }

        PdfFastScroller(
            state = pdfViewerState,
            contentTop = bars.top,
            modifier =
                Modifier.align(Alignment.TopEnd).padding(top = bars.top, bottom = bars.bottom),
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

        ViewerBar(
            visible = chrome.isVisible,
            fromTop = true,
            onHeightMeasured = { bars.measureTop(it, density) },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            PdfViewerTopBar(
                title = documentInfo.title ?: documentInfo.filename,
                description = documentInfo.description,
                showBackButton = showBackButton,
                onBack = onBack,
                // Only for a document that is on screen: what cannot be shown is not kept.
                onSaveToLibrary =
                    if (state.canSaveToLibrary && pdfViewerState.isLoaded) {
                        { onIntent(PdfViewerIntent.SaveToLibrary) }
                    } else null,
                isSavingToLibrary = state.isSavingToLibrary,
                onShare = if (canHandOff) ({ onIntent(PdfViewerIntent.Share) }) else null,
                onPrint =
                    if (pdfViewerState.isLoaded) ({ onIntent(PdfViewerIntent.Print) }) else null,
                onOpenWith = openWith,
                onDetails = onOpenDetails,
                hazeState = hazeState,
            )
        }

        ViewerBar(
            // Nothing to page through or zoom in a failed document. Kept while loading: the
            // height is the content padding, and learning it after the layout would move a
            // restored position.
            visible = chrome.isVisible && pdfViewerState.error == null,
            fromTop = false,
            onHeightMeasured = { bars.measureBottom(it, density) },
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .padding(
                        bottom =
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                                16.dp
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
                onPageClick = { onGoToPage(pdfViewerState.currentPage, pdfViewerState.pageCount) },
                onZoomIn = {
                    scope.launch { pdfViewerState.animateZoomTo(pdfViewerState.zoom * ZoomStep) }
                },
                onZoomOut = {
                    scope.launch { pdfViewerState.animateZoomTo(pdfViewerState.zoom / ZoomStep) }
                },
                onResetZoom = { scope.launch { pdfViewerState.animateResetZoom() } },
                onFitModeChange = { onIntent(PdfViewerIntent.SetFitMode(it)) },
                onNightModeToggle = { onIntent(PdfViewerIntent.ToggleNightMode) },
                hazeState = hazeState,
            )
        }
    }
}

/**
 * The bars' measured heights, kept while they are hidden. They reach the viewer as constant content
 * padding: pages start below the top bar, end above the bottom one, and scroll underneath them. An
 * animated padding on the viewer itself resized the viewport, and relaid the document out, on every
 * frame the bars moved.
 */
@Stable
private class BarHeights {
    var top by mutableStateOf(0.dp)
        private set

    var bottom by mutableStateOf(0.dp)
        private set

    fun topPx(density: Density): Float = with(density) { top.toPx() }

    fun measureTop(heightPx: Int, density: Density) {
        top = with(density) { heightPx.toDp() }
    }

    fun measureBottom(heightPx: Int, density: Density) {
        bottom = with(density) { heightPx.toDp() }
    }
}

/**
 * A bar that fades and slides in from the top or bottom edge, reporting its height once measured. A
 * zero height is not reported: it is the bar being laid out, not its size.
 */
@Composable
private fun ViewerBar(
    visible: Boolean,
    fromTop: Boolean,
    onHeightMeasured: (heightPx: Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val offscreen: (Int) -> Int = if (fromTop) { height -> -height } else { height -> height }
    AnimatedVisibility(
        visible = visible,
        modifier =
            Modifier.onSizeChanged { if (it.height > 0) onHeightMeasured(it.height) }
                .then(modifier),
        enter =
            fadeIn(motion.slowEffectsSpec()) +
                slideInVertically(motion.fastSpatialSpec(), initialOffsetY = offscreen),
        exit =
            fadeOut(motion.slowEffectsSpec()) +
                slideOutVertically(motion.fastSpatialSpec(), targetOffsetY = offscreen),
    ) {
        content()
    }
}

/**
 * Makes the touches only this screen can decide: a tap on a link, and a long press on a word.
 *
 * It reads the latest [state] when a touch arrives, not when the handler is built, so one handler
 * lasts as long as its [selectionDrag].
 */
@Composable
private fun rememberSelectionHandler(
    state: PdfViewerUiState,
    pdfViewerState: PdfViewerState,
    selectionDrag: SelectionDrag,
    onIntent: (PdfViewerIntent) -> Unit,
): PdfInteractionHandler {
    val haptics = LocalHapticFeedback.current
    val latest by rememberUpdatedState(state)
    val send by rememberUpdatedState(onIntent)
    return remember(pdfViewerState, selectionDrag, haptics) {
        object : PdfInteractionHandler {
            // A tap on a link answers at once (E3). Not while text is selected: that tap lets go
            // of the selection, as anywhere else.
            override fun claimsTap(event: PdfTapEvent): Boolean =
                latest.selection == null && latest.pageLinks.linkAt(event) != null

            override fun onTap(event: PdfTapEvent) {
                val (page, link) = latest.pageLinks.linkAt(event) ?: return
                send(PdfViewerIntent.TapLink(page, link, pdfViewerState.pageCount))
            }

            // Decided at once, from the text already read: a long press cannot wait.
            override fun onLongPress(event: PdfTapEvent): Boolean {
                val page = event.pageIndex ?: return false
                val at = event.pagePosition ?: return false
                val outcome =
                    SelectionInteraction.longPress(
                        latest.pageText,
                        page,
                        NormalizedPoint(at.x, at.y),
                    )
                return when (outcome) {
                    is LongPressOutcome.Select -> {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        send(PdfViewerIntent.Select(outcome.selection))
                        // The pressed word stays selected while the finger drags on.
                        selectionDrag.start(
                            anchor = outcome.selection,
                            current = outcome.selection,
                            at = event.position,
                        )
                        true
                    }

                    is LongPressOutcome.NoText -> {
                        haptics.performHapticFeedback(HapticFeedbackType.Reject)
                        send(PdfViewerIntent.NothingToSelect(outcome.reason))
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
}

/** The link under a tap, with its page, from the links already read. */
private fun Map<Int, List<PageLink>>.linkAt(event: PdfTapEvent): Pair<Int, PageLink>? {
    val page = event.pageIndex ?: return null
    val at = event.pagePosition ?: return null
    val point = NormalizedPoint(at.x, at.y)
    val link =
        this[page]?.firstOrNull { link -> link.bounds.any { it.distanceTo(point) <= LinkSlop } }
            ?: return null
    return page to link
}

/**
 * Scrolls to a page asked for by *Go to page*, which cannot reach this state itself. A request is
 * taken once it has been scrolled to.
 */
@Composable
private fun ConsumePageRequests(document: ViewerDocumentRef, pdfViewerState: PdfViewerState) {
    val pageRequests: ViewerPageRequests = koinInject()
    LaunchedEffect(document, pdfViewerState) {
        pageRequests.observe(document).filterNotNull().collect { page ->
            if (pageRequests.consume(document, page)) pdfViewerState.animateScrollToPage(page)
        }
    }
}

/** Reads the text of the pages on screen as they arrive; a fling only reads where it stops. */
@OptIn(FlowPreview::class)
@Composable
private fun ReadVisiblePages(pdfViewerState: PdfViewerState, onIntent: (PdfViewerIntent) -> Unit) {
    val send by rememberUpdatedState(onIntent)
    LaunchedEffect(pdfViewerState) {
        snapshotFlow { pdfViewerState.visiblePages }
            .distinctUntilChanged()
            .debounce(VisiblePagesSettle)
            .collect { send(PdfViewerIntent.VisiblePagesChanged(it)) }
    }
}

/** A preview belongs to where its link is: moving the document closes it (D3). */
@Composable
private fun DismissLinkPreviewOnMove(
    pdfViewerState: PdfViewerState,
    previewShown: Boolean,
    onIntent: (PdfViewerIntent) -> Unit,
) {
    val send by rememberUpdatedState(onIntent)
    LaunchedEffect(previewShown) {
        if (!previewShown) return@LaunchedEffect
        // The first emission is the position the preview opened at.
        snapshotFlow { Triple(pdfViewerState.panX, pdfViewerState.panY, pdfViewerState.zoom) }
            .drop(1)
            .first()
        send(PdfViewerIntent.DismissLinkPreview)
    }
}

/** Carries out what the ViewModel cannot, for want of an activity, and shows what it has to say. */
@Composable
private fun HandlePdfViewerEffects(
    viewModel: PdfViewerViewModel,
    pdfViewerState: PdfViewerState,
    contentTop: () -> Float,
    onConfirmSaveCopy: (documentUuid: String) -> Unit,
    onOpenInLibrary: (documentUuid: String) -> Unit,
) {
    val confirmSaveCopy by rememberUpdatedState(onConfirmSaveCopy)
    val openInLibrary by rememberUpdatedState(onOpenInLibrary)
    val activity = requireNotNull(LocalActivity.current) { "The PDF viewer needs an activity" }
    val printer: DocumentPrinter = koinInject { parametersOf(activity) }
    val linkOpener: LinkOpener = koinInject { parametersOf(activity) }
    val notifications = LocalNotificationsService.current
    val clipboard = LocalClipboard.current
    val copied = stringResource(R.string.viewer_text_copied)
    val noApp = stringResource(DesignsystemR.string.link_no_app)
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    // The browser bar takes the app's colors.
    val look =
        LinkLook(
            toolbarColor = MaterialTheme.colorScheme.surfaceContainer.toArgb(),
            darkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f,
        )
    val latestLook by rememberUpdatedState(look)

    LaunchedEffect(viewModel, printer) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is PdfViewerEffect.Print -> printer.print(effect.document, effect.jobName)
                is PdfViewerEffect.ConfirmSaveCopy -> confirmSaveCopy(effect.documentUuid)
                is PdfViewerEffect.AlreadyInLibrary ->
                    notifications.show(
                        InAppNotification(
                            message = resources.getString(R.string.already_in_library_title),
                            type = NotificationType.Info,
                            action =
                                NotificationAction(
                                    label = resources.getString(DesignsystemR.string.open)
                                ) {
                                    openInLibrary(effect.documentUuid)
                                },
                            duration = AlreadyInLibraryNotice,
                        )
                    )

                is PdfViewerEffect.OpenLink ->
                    if (!linkOpener.open(effect.action, latestLook)) {
                        notifications.show(
                            InAppNotification(message = noApp, type = NotificationType.Error)
                        )
                    }

                is PdfViewerEffect.GoToPage -> {
                    // Where the reader was, to take them back: the point at the top of the
                    // content area, which is where the jump will put its target too.
                    val back = pdfViewerState.readingPoint(contentTop())
                    scope.launch {
                        pdfViewerState.animateScrollTo(
                            effect.page,
                            effect.position?.let { Offset(it.x, it.y) },
                        )
                    }
                    notifications.show(
                        InAppNotification(
                            message =
                                resources.getString(R.string.link_went_to_page, effect.page + 1),
                            action =
                                NotificationAction(
                                    label =
                                        resources.getString(
                                            R.string.link_back_to_page,
                                            effect.from + 1,
                                        )
                                ) {
                                    scope.launch {
                                        pdfViewerState.animateScrollTo(
                                            back?.first ?: effect.from,
                                            back?.second,
                                        )
                                    }
                                },
                        )
                    )
                }

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

/**
 * Tells [onPosition] where the reader is, for the next time the document is opened: a moment after
 * they stop moving, and when they leave. Never on every frame of a scroll, since it is written
 * down.
 *
 * What is reported on leaving is the last position seen, not one read then: by the time this is
 * disposed the engine may already have let go of the document, and would answer with the middle of
 * the current page.
 */
@OptIn(FlowPreview::class)
@Composable
private fun ReportReadingPosition(
    state: PdfViewerState,
    seen: LastSeen,
    onPosition: (ReadingPosition) -> Unit,
) {
    val report by rememberUpdatedState(onPosition)

    LaunchedEffect(state) {
        // Only once the document is laid out: before that the position is where the state was
        // told to start, and nothing on screen yet.
        snapshotFlow { if (state.isLoaded) state.readingPosition else null }
            .filterNotNull()
            .distinctUntilChanged()
            .onEach { seen.position = it }
            .debounce(ReadingPositionSettle)
            .collect {
                seen.reported = it
                report(it.toDomain())
            }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, state) {
        val reportLastSeen = {
            val position = seen.position
            if (position != null && position != seen.reported) {
                seen.reported = position
                report(position.toDomain())
            }
        }
        // Stopping is the last moment the app is sure to get: it can be killed afterwards without
        // another word.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) reportLastSeen()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            reportLastSeen()
        }
    }
}

/** Where the reader was last seen, and the last place the ViewModel was told about. */
private class LastSeen {
    var position: PdfReadingPosition? = null
    var reported: PdfReadingPosition? = null
}

private fun ReadingPosition.toEngine() =
    PdfReadingPosition(pageIndex = pageIndex, fraction = offset)

private fun PdfReadingPosition.toDomain() =
    ReadingPosition(pageIndex = pageIndex, offset = fraction)

/**
 * The page and point at the top of the content area, [contentTop] pixels down the viewer: where a
 * jump puts its target, so the way back returns the reader to the line they were on.
 */
private fun PdfViewerState.readingPoint(contentTop: Float): Pair<Int, Offset>? {
    val y = contentTop + 1f
    val page =
        visiblePages.firstOrNull { index ->
            pageRectInViewer(index)?.let { y <= it.bottom } == true
        } ?: return null
    val rect = pageRectInViewer(page) ?: return null
    // Between two pages, the top of the next one.
    val hit = hitTest(Offset(rect.center.x, maxOf(rect.top + 1f, y)))
    val at = hit.pagePosition ?: return null
    return (hit.pageIndex ?: page) to at
}

/** What TalkBack says for a link: where it leads. */
@Composable
private fun describeLink(link: PageLink, resolve: ResolveLinkUseCase, pageCount: Int): String =
    when (val action = resolve(link, pageCount)) {
        is LinkAction.OpenWeb -> stringResource(R.string.link_a11y_web, action.host)
        is LinkAction.ComposeEmail -> stringResource(R.string.link_a11y_email, action.address)
        is LinkAction.Dial -> stringResource(R.string.link_a11y_phone, action.number)
        is LinkAction.GoTo -> stringResource(R.string.link_a11y_page, action.pageIndex + 1)
        is LinkAction.Blocked -> stringResource(R.string.link_a11y_blocked)
    }

/** How far off a link a tap still counts as on it, in page units: about a finger's slack. */
private const val LinkSlop = 0.01f

private fun ViewerFitMode.toEngine(): FitMode =
    when (this) {
        ViewerFitMode.WIDTH -> FitMode.WIDTH
        ViewerFitMode.HEIGHT -> FitMode.HEIGHT
        ViewerFitMode.BOTH -> FitMode.BOTH
        ViewerFitMode.PROPORTIONAL -> FitMode.PROPORTIONAL
    }

/** How long the pages on screen must stay put before their text is read. */
private val VisiblePagesSettle = 150.milliseconds

/**
 * How long the reader stays put before where they are is written down. Long enough that leafing
 * through a document writes nothing; what is lost by leaving sooner is written on the way out.
 */
private val ReadingPositionSettle = 1.seconds

/** Each zoom button press scales by this much, animated. */
private const val ZoomStep = 1.25f

/**
 * How long the reader is told that the library already has the document. Longer than a message that
 * only informs: there is something to decide, and they have just started looking at the page.
 */
private val AlreadyInLibraryNotice = 8.seconds
