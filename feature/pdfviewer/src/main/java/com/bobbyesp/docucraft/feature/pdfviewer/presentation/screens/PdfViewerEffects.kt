/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.screens

import android.content.ClipData
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bobbyesp.docucraft.core.designsystem.R as DesignsystemR
import com.bobbyesp.docucraft.core.domain.notifications.InAppNotification
import com.bobbyesp.docucraft.core.domain.notifications.NotificationAction
import com.bobbyesp.docucraft.core.domain.notifications.NotificationType
import com.bobbyesp.docucraft.core.ui.common.LocalNotificationsService
import com.bobbyesp.docucraft.core.ui.events.UiEvent
import com.bobbyesp.docucraft.feature.pdfviewer.R
import com.bobbyesp.docucraft.feature.pdfviewer.domain.actions.DocumentPrinter
import com.bobbyesp.docucraft.feature.pdfviewer.domain.links.LinkLook
import com.bobbyesp.docucraft.feature.pdfviewer.domain.links.LinkOpener
import com.bobbyesp.docucraft.feature.pdfviewer.domain.model.ViewerDocumentRef
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.ViewerLoadError
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.search.ViewerSearchState
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.contract.PdfViewerEffect
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.contract.PdfViewerIntent
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.pages.ViewerPageRequests
import com.bobbyesp.docucraft.library.domain.model.ReadingPosition
import com.composepdf.PdfReadingPosition
import com.composepdf.PdfViewerState
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/**
 * Reports where the reader is a moment after they stop moving, and when they leave; never on every
 * scroll frame.
 *
 * On leaving it reports the last position seen, because the engine may already have released the
 * document by then and would answer with the middle of the page.
 */
@OptIn(FlowPreview::class)
@Composable
internal fun ReportReadingPosition(
    pdf: PdfViewerState,
    lastSeen: LastSeen,
    onPosition: (ReadingPosition) -> Unit,
) {
    val report by rememberUpdatedState(onPosition)

    LaunchedEffect(pdf) {
        // Before the layout, the position is only where the state was told to start.
        snapshotFlow { if (pdf.isLoaded) pdf.readingPosition else null }
            .filterNotNull()
            .distinctUntilChanged()
            .onEach { lastSeen.position = it }
            .debounce(ReadingPositionSettle)
            .collect {
                lastSeen.reported = it
                report(it.toDomain())
            }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, pdf) {
        val reportLastSeen = {
            val position = lastSeen.position
            if (position != null && position != lastSeen.reported) {
                lastSeen.reported = position
                report(position.toDomain())
            }
        }
        // Stopping is the last moment the app is sure to get: it can be killed afterwards.
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

/** Scrolls to the page *Go to page* asked for, which cannot reach this state itself. */
@Composable
internal fun ConsumePageRequests(document: ViewerDocumentRef, pdf: PdfViewerState) {
    val pageRequests: ViewerPageRequests = koinInject()
    LaunchedEffect(document, pdf) {
        pageRequests.observe(document).filterNotNull().collect { page ->
            if (pageRequests.consume(document, page)) pdf.animateScrollToPage(page)
        }
    }
}

/** A fling only reads the text of the pages where it stops. */
@OptIn(FlowPreview::class)
@Composable
internal fun ReadVisiblePages(pdf: PdfViewerState, onIntent: (PdfViewerIntent) -> Unit) {
    val send by rememberUpdatedState(onIntent)
    LaunchedEffect(pdf) {
        snapshotFlow { pdf.visiblePages }
            .distinctUntilChanged()
            .debounce(VisiblePagesSettle)
            .collect { send(PdfViewerIntent.VisiblePagesChanged(it)) }
    }
}

/** A preview belongs to where its link is: moving the document closes it (D3). */
@Composable
internal fun DismissLinkPreviewOnMove(
    pdf: PdfViewerState,
    previewShown: Boolean,
    onIntent: (PdfViewerIntent) -> Unit,
) {
    val send by rememberUpdatedState(onIntent)
    LaunchedEffect(pdf, previewShown) {
        if (!previewShown) return@LaunchedEffect
        // The first emission is where the preview opened.
        snapshotFlow { Triple(pdf.panX, pdf.panY, pdf.zoom) }.drop(1).first()
        send(PdfViewerIntent.DismissLinkPreview)
    }
}

/** Whether the file was there is only known once the engine has tried to read it. */
@Composable
internal fun ReportDocumentLoad(
    pdf: PdfViewerState,
    loadError: ViewerLoadError?,
    onIntent: (PdfViewerIntent) -> Unit,
) {
    val send by rememberUpdatedState(onIntent)
    LaunchedEffect(pdf.isLoaded, loadError) {
        when {
            loadError != null -> send(PdfViewerIntent.DocumentFailedToLoad(loadError))
            pdf.isLoaded -> send(PdfViewerIntent.DocumentLoaded(pdf.pageCount))
        }
    }
}

/**
 * Tells the ViewModel what is being searched for as it is typed, and when the search is over.
 *
 * Nothing is sent while the document loads, and loading is not a closed search: after a rotation
 * the ViewModel keeps its search and is only told the same query, which changes nothing. After a
 * process death it is told a query it no longer has, and searches.
 */
@Composable
internal fun ReportSearchQuery(
    search: ViewerSearchState,
    pdf: PdfViewerState,
    onIntent: (PdfViewerIntent) -> Unit,
) {
    val send by rememberUpdatedState(onIntent)
    LaunchedEffect(search, pdf) {
        // A search never opened has nothing to close; closing would also clear the highlight of
        // the query the document was opened on.
        var wasOpen = false
        snapshotFlow {
            when {
                !pdf.isLoaded -> null
                search.isOpen -> SearchReport.Query(search.field.text.toString())
                else -> SearchReport.Closed
            }
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { report ->
                when (report) {
                    is SearchReport.Query -> {
                        wasOpen = true
                        send(PdfViewerIntent.SearchQueryChanged(report.text, pdf.currentPage))
                    }
                    SearchReport.Closed ->
                        if (wasOpen) {
                            wasOpen = false
                            send(PdfViewerIntent.CloseSearch)
                        }
                }
            }
    }
}

/**
 * Lights the matches of the query a document was opened from, without opening the find bar, and
 * puts them out when the reader first touches the document. It runs once per entry: after a
 * rotation the reader has either already moved on or still sees the matches.
 */
@Composable
internal fun HighlightArrivalQuery(
    pdf: PdfViewerState,
    search: ViewerSearchState,
    query: String?,
    onIntent: (PdfViewerIntent) -> Unit,
) {
    if (query.isNullOrBlank()) return
    val send by rememberUpdatedState(onIntent)
    var handled by rememberSaveable(query) { mutableStateOf(false) }
    LaunchedEffect(pdf, query) {
        if (handled) return@LaunchedEffect
        snapshotFlow { pdf.isLoaded }.first { it }
        if (search.isOpen) return@LaunchedEffect
        send(PdfViewerIntent.ShowMatches(query, pdf.currentPage))
        snapshotFlow { pdf.isGestureActive }.first { it }
        handled = true
        if (!search.isOpen) send(PdfViewerIntent.CloseSearch)
    }
}

private sealed interface SearchReport {
    data class Query(val text: String) : SearchReport

    data object Closed : SearchReport
}

/** Touching the document while searching dismisses the keyboard, which covers the matches. */
@Composable
internal fun DismissKeyboardOnTouch(screenState: PdfViewerScreenState) {
    val pdf = screenState.pdf
    val searching = screenState.search.isOpen
    LaunchedEffect(screenState, searching) {
        if (!searching) return@LaunchedEffect
        snapshotFlow { pdf.isGestureActive }.filter { it }.collect { screenState.dismissKeyboard() }
    }
}

/** Carries out what the ViewModel cannot, for want of an activity. */
@Composable
internal fun HandlePdfViewerEffects(
    effects: Flow<PdfViewerEffect>,
    screenState: PdfViewerScreenState,
    onConfirmSaveCopy: (documentUuid: String) -> Unit,
    onOpenInLibrary: (documentUuid: String) -> Unit,
) {
    val activity = requireNotNull(LocalActivity.current) { "The PDF viewer needs an activity" }
    val printer: DocumentPrinter = koinInject { parametersOf(activity) }
    val linkOpener: LinkOpener = koinInject { parametersOf(activity) }
    val notifications = LocalNotificationsService.current
    val clipboard = LocalClipboard.current
    val colorScheme = MaterialTheme.colorScheme
    // Custom Tabs take the app's colors.
    val linkLook =
        LinkLook(
            toolbarColor = colorScheme.surfaceContainer.toArgb(),
            darkTheme = colorScheme.surface.luminance() < 0.5f,
        )

    // The engine's state is replaced when the document moves into the library; effects must reach
    // the one on screen, not the one this collection started with.
    val screen by rememberUpdatedState(screenState)
    val look by rememberUpdatedState(linkLook)
    val resources by rememberUpdatedState(LocalResources.current)
    val confirmSaveCopy by rememberUpdatedState(onConfirmSaveCopy)
    val openInLibrary by rememberUpdatedState(onOpenInLibrary)

    LaunchedEffect(effects, printer, linkOpener) {
        effects.collect { effect ->
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
                    if (!linkOpener.open(effect.action, look)) {
                        notifications.show(
                            InAppNotification(
                                message = resources.getString(DesignsystemR.string.link_no_app),
                                type = NotificationType.Error,
                            )
                        )
                    }

                is PdfViewerEffect.GoToPage -> {
                    // Read before the jump, which lands its target on that same line.
                    val back = screen.readingPoint()
                    screen.scrollTo(effect.page, effect.position?.let { Offset(it.x, it.y) })
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
                                    screen.scrollTo(back?.first ?: effect.from, back?.second)
                                },
                        )
                    )
                }

                is PdfViewerEffect.RevealMatch -> screen.reveal(effect.page, effect.area)

                is PdfViewerEffect.CopyText -> {
                    val label = resources.getString(R.string.viewer_text_copied)
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, effect.text)))
                    // From Android 13 the system confirms a copy itself.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        notifications.show(
                            InAppNotification(message = label, type = NotificationType.Success)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun HandleViewerMessages(events: Flow<UiEvent>) {
    val notifications = LocalNotificationsService.current
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is UiEvent.ShowMessage ->
                    notifications.show(
                        InAppNotification(message = event.message, type = event.type)
                    )
            }
        }
    }
}

private fun PdfReadingPosition.toDomain() =
    ReadingPosition(pageIndex = pageIndex, offset = fraction)

private val VisiblePagesSettle = 150.milliseconds

/** Long enough that leafing through a document writes nothing; leaving writes the last position. */
private val ReadingPositionSettle = 1.seconds

/** Longer than an informational message: it offers a decision. */
private val AlreadyInLibraryNotice = 8.seconds
