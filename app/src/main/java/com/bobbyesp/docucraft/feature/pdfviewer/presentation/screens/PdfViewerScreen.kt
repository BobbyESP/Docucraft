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
import com.bobbyesp.docucraft.core.domain.notifications.NotificationAction
import com.bobbyesp.docucraft.core.domain.notifications.NotificationType
import com.bobbyesp.docucraft.core.presentation.common.LocalNotificationsService
import com.bobbyesp.docucraft.core.util.events.UiEvent
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
import com.bobbyesp.documentcontent.PageLink
import com.composepdf.FitMode
import com.composepdf.PdfInteractionHandler
import com.composepdf.PdfLayoutSpec
import com.composepdf.PdfSource
import com.composepdf.PdfTapEvent
import com.composepdf.PdfViewer
import com.composepdf.PdfViewerDefaults
import com.composepdf.PdfViewerState
import com.composepdf.PdfZoomSpec
import com.composepdf.ScrollDirection
import com.composepdf.rememberPdfViewerState
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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

    val chrome = rememberViewerChromeState()

    // The bars' own measured heights, kept while they are hidden. Handed to the viewer as constant
    // content padding: pages start below the top bar and end above the bottom one, and scroll
    // underneath them. It used to be an animated padding on the viewer itself, which resized the
    // viewport, and relaid the document out, on every frame the bars moved (V9).
    val density = LocalDensity.current
    var topBarHeight by remember { mutableStateOf(0.dp) }
    var bottomBarHeight by remember { mutableStateOf(0.dp) }

    HandlePdfViewerEffects(
        viewModel = viewModel,
        pdfViewerState = pdfViewerState,
        contentTop = { with(density) { topBarHeight.toPx() } },
    )

    // ---------------------------------------------------------------- text selection

    // The text of the pages on screen is read as they arrive; a fling only reads where it stops.
    LaunchedEffect(pdfViewerState) {
        snapshotFlow { pdfViewerState.visiblePages }
            .distinctUntilChanged()
            .debounce(VisiblePagesSettle)
            .collect { viewModel.onSendIntent(PdfViewerIntent.VisiblePagesChanged(it)) }
    }

    val selectionDrag = rememberSelectionDrag(pdfViewerState)
    val resolveLink: ResolveLinkUseCase = koinInject()
    val haptics = LocalHapticFeedback.current
    val latestPageText by rememberUpdatedState(state.pageText)
    val latestPageLinks by rememberUpdatedState(state.pageLinks)
    val latestSelection by rememberUpdatedState(state.selection)
    val selectionHandler =
        remember(selectionDrag, viewModel, haptics) {
            object : PdfInteractionHandler {
                /** The link under a tap, if any, from the links already read. */
                fun linkAt(event: PdfTapEvent): Pair<Int, PageLink>? {
                    val page = event.pageIndex ?: return null
                    val at = event.pagePosition ?: return null
                    val point = NormalizedPoint(at.x, at.y)
                    val link =
                        latestPageLinks[page]?.firstOrNull { link ->
                            link.bounds.any { it.distanceTo(point) <= LinkSlop }
                        } ?: return null
                    return page to link
                }

                // A tap on a link answers at once (E3). Not while text is selected: that tap lets
                // go of the selection, as anywhere else.
                override fun claimsTap(event: PdfTapEvent): Boolean =
                    latestSelection == null && linkAt(event) != null

                override fun onTap(event: PdfTapEvent) {
                    val (page, link) = linkAt(event) ?: return
                    viewModel.onSendIntent(
                        PdfViewerIntent.TapLink(page, link, pdfViewerState.pageCount)
                    )
                }

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
                                // The pressed word stays selected while the finger drags on.
                                anchor = outcome.selection,
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

    // Back closes a link's preview, then lets go of the selection, before it leaves the viewer.
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = state.linkPreview != null || state.selection != null,
        onBackCompleted = {
            viewModel.onSendIntent(
                if (state.linkPreview != null) PdfViewerIntent.DismissLinkPreview
                else PdfViewerIntent.ClearSelection
            )
        },
    )

    // A preview belongs to where the link is: moving the document closes it (D3).
    LaunchedEffect(state.linkPreview != null) {
        if (state.linkPreview == null) return@LaunchedEffect
        snapshotFlow { Triple(pdfViewerState.panX, pdfViewerState.panY, pdfViewerState.zoom) }
            .drop(1)
            .first()
        viewModel.onSendIntent(PdfViewerIntent.DismissLinkPreview)
    }

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
            // A tap closes a link's preview or lets go of a selection; otherwise it shows or hides
            // the bars.
            onTap = {
                when {
                    state.linkPreview != null ->
                        viewModel.onSendIntent(PdfViewerIntent.DismissLinkPreview)
                    state.selection != null ->
                        viewModel.onSendIntent(PdfViewerIntent.ClearSelection)
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
                    contentTop = topBarHeight,
                    contentBottomInset = bottomBarHeight,
                    nightMode = display.nightMode,
                    onSelect = { viewModel.onSendIntent(PdfViewerIntent.Select(it)) },
                    onCopy = { viewModel.onSendIntent(PdfViewerIntent.CopySelection) },
                    onSelectAll = { viewModel.onSendIntent(PdfViewerIntent.SelectAll) },
                )
                LinkLayer(
                    pageLinks = state.pageLinks,
                    preview = state.linkPreview,
                    describe = { describeLink(it, resolveLink, pdfViewerState.pageCount) },
                    onTapLink = { page, link ->
                        viewModel.onSendIntent(
                            PdfViewerIntent.TapLink(page, link, pdfViewerState.pageCount)
                        )
                    },
                    onOpen = { viewModel.onSendIntent(PdfViewerIntent.OpenPreviewedLink) },
                    onCopy = { viewModel.onSendIntent(PdfViewerIntent.CopyPreviewedLink) },
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
private fun HandlePdfViewerEffects(
    viewModel: PdfViewerViewModel,
    pdfViewerState: PdfViewerState,
    contentTop: () -> Float,
) {
    val activity = requireNotNull(LocalActivity.current) { "The PDF viewer needs an activity" }
    val printer: DocumentPrinter = koinInject { parametersOf(activity) }
    val linkOpener: LinkOpener = koinInject { parametersOf(activity) }
    val notifications = LocalNotificationsService.current
    val clipboard = LocalClipboard.current
    val copied = stringResource(R.string.viewer_text_copied)
    val noApp = stringResource(R.string.link_no_app)
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    // The browser bar takes the app's colours (D4).
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

/** Each zoom button press scales by this much, animated. */
private const val ZoomStep = 1.25f
