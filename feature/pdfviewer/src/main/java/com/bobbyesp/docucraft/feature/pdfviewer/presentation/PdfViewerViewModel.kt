/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.bobbyesp.docucraft.core.designsystem.R as DesignsystemR
import com.bobbyesp.docucraft.core.domain.StringProvider
import com.bobbyesp.docucraft.core.domain.analytics.AnalyticsEvent
import com.bobbyesp.docucraft.core.domain.model.ViewerDisplaySettings
import com.bobbyesp.docucraft.core.domain.model.ViewerFitMode
import com.bobbyesp.docucraft.core.domain.notifications.NotificationType
import com.bobbyesp.docucraft.core.domain.repository.AnalyticsHelper
import com.bobbyesp.docucraft.core.domain.repository.logScreenView
import com.bobbyesp.docucraft.core.ui.events.UiEvent
import com.bobbyesp.docucraft.core.ui.viewmodel.BaseViewModel
import com.bobbyesp.docucraft.feature.pdfviewer.R
import com.bobbyesp.docucraft.feature.pdfviewer.domain.actions.DocumentOpener
import com.bobbyesp.docucraft.feature.pdfviewer.domain.links.LinkAction
import com.bobbyesp.docucraft.feature.pdfviewer.domain.links.ResolveLinkUseCase
import com.bobbyesp.docucraft.feature.pdfviewer.domain.model.ViewerDocumentRef
import com.bobbyesp.docucraft.feature.pdfviewer.domain.search.MatchRef
import com.bobbyesp.docucraft.feature.pdfviewer.domain.search.SearchMatches
import com.bobbyesp.docucraft.feature.pdfviewer.domain.usecase.ObserveViewerDisplaySettingsUseCase
import com.bobbyesp.docucraft.feature.pdfviewer.domain.usecase.ObserveViewerDocumentUseCase
import com.bobbyesp.docucraft.feature.pdfviewer.domain.usecase.UpdateViewerDisplaySettingsUseCase
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.ViewerLoadError
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.contract.LinkPreview
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.contract.PdfViewerEffect
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.contract.PdfViewerIntent
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.contract.PdfViewerUiState
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.contract.ViewerDocumentState
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.contract.ViewerStart
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.search.DocumentSearch
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.selection.PageTextState
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.selection.SelectionInteraction
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.selection.TextUnavailable
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.selection.toTextState
import com.bobbyesp.docucraft.library.domain.model.BasicDocument
import com.bobbyesp.docucraft.library.domain.model.Document
import com.bobbyesp.docucraft.library.domain.model.DocumentAvailability
import com.bobbyesp.docucraft.library.domain.model.ReadingPosition
import com.bobbyesp.docucraft.library.domain.sharing.DocumentSharer
import com.bobbyesp.docucraft.library.domain.usecase.DescribeLinkedDocumentUseCase
import com.bobbyesp.docucraft.library.domain.usecase.GetReadingPositionUseCase
import com.bobbyesp.docucraft.library.domain.usecase.ObserveDocumentUseCase
import com.bobbyesp.docucraft.library.domain.usecase.RecordDocumentAvailabilityUseCase
import com.bobbyesp.docucraft.library.domain.usecase.RecordDocumentOpenedUseCase
import com.bobbyesp.docucraft.library.domain.usecase.RegisterLinkedDocumentUseCase
import com.bobbyesp.docucraft.library.domain.usecase.RememberReadingPositionUseCase
import com.bobbyesp.docucraft.library.domain.usecase.SaveLinkedToLibraryUseCase
import com.bobbyesp.docucraft.library.domain.usecase.SaveToLibraryOutcome
import com.bobbyesp.documentcontent.DocumentSource
import com.bobbyesp.documentcontent.FoldedText
import com.bobbyesp.documentcontent.NormalizedRect
import com.bobbyesp.documentcontent.PageContentProvider
import com.bobbyesp.documentcontent.PageContentResult
import com.bobbyesp.documentcontent.PageContentSession
import com.bobbyesp.documentcontent.TextSearch
import com.bobbyesp.documentcontent.TextSelection
import com.bobbyesp.scanner.ContentRef
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The viewer's state holder, one per navigation entry (or per external document, in
 * `PdfViewerActivity`).
 *
 * It holds what must outlive the composition:
 * - the display settings (D2): remembered per document for the session, with a copy in the
 *   [SavedStateHandle] because a process death is still the same session to the user (A1);
 * - the reading position, written down for the next time; the live position stays in the
 *   composition;
 * - text selection and search: a content session opened on first need and closed with the
 *   ViewModel, the text of the pages near the screen, the selection and the search. Copying and
 *   searching read pages that are not on screen, so they happen here.
 */
class PdfViewerViewModel(
    private val ref: ViewerDocumentRef,
    private val savedStateHandle: SavedStateHandle,
    observeDocument: ObserveViewerDocumentUseCase,
    observeDisplaySettings: ObserveViewerDisplaySettingsUseCase,
    private val updateDisplaySettings: UpdateViewerDisplaySettingsUseCase,
    private val documentSharer: DocumentSharer,
    private val documentOpener: DocumentOpener,
    private val stringProvider: StringProvider,
    private val analyticsHelper: AnalyticsHelper,
    private val contentProvider: PageContentProvider,
    private val resolveLink: ResolveLinkUseCase,
    private val recordOpened: RecordDocumentOpenedUseCase,
    private val recordAvailability: RecordDocumentAvailabilityUseCase,
    private val getReadingPosition: GetReadingPositionUseCase,
    private val rememberReadingPosition: RememberReadingPositionUseCase,
    private val registerLinkedDocument: RegisterLinkedDocumentUseCase,
    private val describeLinkedDocument: DescribeLinkedDocumentUseCase,
    private val observeCatalogueDocument: ObserveDocumentUseCase,
    private val saveToLibrary: SaveLinkedToLibraryUseCase,
    private val longLived: CoroutineScope,
) :
    BaseViewModel<PdfViewerIntent, PdfViewerUiState, PdfViewerEffect>(
        initialState = PdfViewerUiState()
    ) {

    /**
     * The uuid the catalogue knows this document by. An external document gets one by being
     * registered (which is what shows it in Recents); `null` if that failed, and then the document
     * is shown but nothing is recorded about it.
     *
     * Declared above `init`, which uses it.
     */
    private val catalogueUuid: Deferred<String?> = viewModelScope.async {
        when (ref) {
            is ViewerDocumentRef.Catalogued -> ref.uuid
            is ViewerDocumentRef.External ->
                runCatching { registerLinkedDocument(ContentRef(ref.uri), ref.displayName) }
                    .getOrNull()
        }
    }

    init {
        analyticsHelper.logScreenView(SCREEN_NAME)

        // Only present after a process death.
        restoredDisplaySettings()?.let { updateDisplaySettings(ref, it) }

        launch {
            observeDisplaySettings(ref).collect { resolved ->
                // A choice inherited from the session memory is kept across process death too.
                if (resolved.isChosen) keepAcrossProcessDeath(resolved.settings)
                setState { copy(display = resolved.settings) }
            }
        }

        // Moves the document to the front of Recents.
        launch { catalogueUuid.await()?.let { recordOpened(it) } }

        launch {
            // An unreadable position must not block showing the document.
            val position =
                catalogueUuid.await()?.let { runCatching { getReadingPosition(it) }.getOrNull() }
            setState { copy(start = ViewerStart(position)) }
        }

        // Observed: the document stops being another app's as soon as it is saved, from anywhere.
        launch {
            val uuid = catalogueUuid.await() ?: return@launch
            observeCatalogueDocument(uuid).collect { document ->
                setState { copy(canSaveToLibrary = document is Document.Linked) }
            }
        }

        launch {
            observeDocument(ref).collect { document ->
                setState {
                    copy(
                        document =
                            document?.let(ViewerDocumentState::Open) ?: ViewerDocumentState.Gone
                    )
                }
            }
        }
    }

    /** Opened on first need, for the document on screen. */
    private var contentSession: Deferred<PageContentSession>? = null
    private var loadingPages: Job? = null

    /** Reasons already explained to the reader, so each is told once per document. */
    private val toldAbout = mutableSetOf<TextUnavailable>()

    /** The viewer reports `DocumentLoaded` again after a rotation; this keeps it to one message. */
    private var toldItIsInLibrary = false

    /** Pages in the document once loaded; what a search has to read. */
    private var pageCount = 0

    /** A query and its starting page, received before the document's pages were counted. */
    private var waitingSearch: Pair<String, Int>? = null

    private var searchJob: Job? = null
    private var revealJob: Job? = null

    /**
     * Each page a search has read, folded, while the search is open. Typing another letter searches
     * these without reading again. A page that failed to read is absent, so it is retried.
     */
    private val searchable = HashMap<Int, FoldedText>()

    /** Pages whose text this device cannot read; why a search may find nothing. */
    private val unreadablePages = mutableSetOf<Int>()

    /**
     * Tells the catalogue whether the file could be reached. A protected or damaged document was
     * reached, so it is still available.
     */
    private fun noteAvailability(availability: DocumentAvailability) = launch {
        val uuid = catalogueUuid.await() ?: return@launch
        recordAvailability(uuid, availability)
    }

    /**
     * Describes another app's document to the catalogue (the use case ignores the app's own). If
     * the library has the same content, offers that copy: it keeps the reader's place and does not
     * depend on another app.
     */
    private fun describe(pageCount: Int?, isProtected: Boolean) = launch {
        val uuid = catalogueUuid.await() ?: return@launch
        val inLibrary =
            describeLinkedDocument(uuid, pageCount = pageCount, isProtected = isProtected)
        if (inLibrary != null && !toldItIsInLibrary) {
            toldItIsInLibrary = true
            sendEffect(PdfViewerEffect.AlreadyInLibrary(inLibrary))
        }
    }

    override fun onHandleIntent(intent: PdfViewerIntent) {
        when (intent) {
            is PdfViewerIntent.DocumentLoaded -> {
                pageCount = intent.pageCount
                noteAvailability(DocumentAvailability.AVAILABLE)
                describe(pageCount = intent.pageCount, isProtected = false)
                waitingSearch?.let { (query, fromPage) ->
                    waitingSearch = null
                    search(query, fromPage)
                }
            }

            is PdfViewerIntent.ReadingPositionChanged -> keepPosition(intent.position)
            is PdfViewerIntent.DocumentFailedToLoad -> {
                noteAvailability(intent.error.availability)
                if (intent.error == ViewerLoadError.PasswordProtected) {
                    describe(pageCount = null, isProtected = true)
                }
            }

            is PdfViewerIntent.VisiblePagesChanged -> loadTextNear(intent.pages)
            is PdfViewerIntent.Select -> setState { copy(selection = intent.selection) }
            PdfViewerIntent.SelectAll -> selectAll()
            PdfViewerIntent.ClearSelection -> setState { copy(selection = null) }
            PdfViewerIntent.CopySelection -> copySelection()
            is PdfViewerIntent.NothingToSelect -> tellOnce(intent.reason)
            is PdfViewerIntent.SearchQueryChanged -> search(intent.query, intent.fromPage)
            is PdfViewerIntent.ShowMatches -> search(intent.query, intent.fromPage)
            is PdfViewerIntent.SearchStep -> step(intent.forward)
            PdfViewerIntent.CloseSearch -> closeSearch()
            is PdfViewerIntent.TapLink -> tapLink(intent)
            PdfViewerIntent.OpenPreviewedLink -> openPreviewedLink()
            PdfViewerIntent.CopyPreviewedLink -> copyPreviewedLink()
            PdfViewerIntent.DismissLinkPreview -> setState { copy(linkPreview = null) }
            is PdfViewerIntent.SetFitMode -> setFitMode(intent)
            PdfViewerIntent.ToggleNightMode -> toggleNightMode()
            PdfViewerIntent.SaveToLibrary -> saveToLibrary()
            PdfViewerIntent.Share -> handOff { documentSharer.share(it) }
            PdfViewerIntent.OpenWith -> handOff { documentOpener.openWith(it) }
            PdfViewerIntent.Print -> print()
        }
    }

    /**
     * Runs in [longLived]: the last position arrives as the viewer leaves and must not be canceled.
     */
    private fun keepPosition(position: ReadingPosition) {
        longLived.launch {
            runCatching {
                val uuid = catalogueUuid.await() ?: return@runCatching
                rememberReadingPosition(uuid, position)
            }
        }
    }

    private val ViewerLoadError.availability: DocumentAvailability
        get() =
            when (this) {
                ViewerLoadError.NotFound -> DocumentAvailability.NOT_FOUND
                ViewerLoadError.AccessDenied -> DocumentAvailability.NO_PERMISSION
                ViewerLoadError.PasswordProtected,
                ViewerLoadError.Damaged,
                ViewerLoadError.Unknown -> DocumentAvailability.AVAILABLE
            }

    private fun setFitMode(intent: PdfViewerIntent.SetFitMode) {
        val display = currentState.display ?: return
        choose(display.copy(fitMode = intent.fitMode))
        logSettingChanged(name = "fit_mode", value = intent.fitMode.name)
    }

    private fun toggleNightMode() {
        val display = currentState.display ?: return
        val chosen = display.copy(nightMode = !display.nightMode)
        choose(chosen)
        logSettingChanged(name = "night_mode", value = chosen.nightMode.toString())
    }

    /** Shown at once and remembered for the session. */
    private fun choose(display: ViewerDisplaySettings) {
        setState { copy(display = display) }
        updateDisplaySettings(ref, display)
    }

    /** A1: a process death empties the session memory but not the entry's saved state. */
    private fun keepAcrossProcessDeath(display: ViewerDisplaySettings) {
        savedStateHandle[KEY_FIT_MODE] = display.fitMode.name
        savedStateHandle[KEY_NIGHT_MODE] = display.nightMode
    }

    private fun restoredDisplaySettings(): ViewerDisplaySettings? {
        val fitMode =
            savedStateHandle.get<String>(KEY_FIT_MODE)?.let { name ->
                ViewerFitMode.entries.firstOrNull { it.name == name }
            } ?: return null
        val nightMode = savedStateHandle.get<Boolean>(KEY_NIGHT_MODE) ?: return null
        return ViewerDisplaySettings(fitMode = fitMode, nightMode = nightMode)
    }

    /** Keeps another app's document in the library. Every outcome is reported; none closes it. */
    private fun saveToLibrary() {
        if (currentState.isSavingToLibrary || !currentState.canSaveToLibrary) return
        setState { copy(isSavingToLibrary = true) }

        launch(onError = { saveFailed() }) {
            val uuid = catalogueUuid.await()
            val outcome = uuid?.let { saveToLibrary(it) } ?: SaveToLibraryOutcome.NothingToSave
            setState { copy(isSavingToLibrary = false) }

            when (outcome) {
                SaveToLibraryOutcome.Saved ->
                    sendUiEvent(
                        UiEvent.ShowMessage(
                            stringProvider.get(R.string.saved_to_library),
                            NotificationType.Success,
                        )
                    )

                is SaveToLibraryOutcome.AlreadyInLibrary ->
                    // A duplicate is only found for a registered document, so `uuid` is set.
                    sendEffect(PdfViewerEffect.ConfirmSaveCopy(checkNotNull(uuid)))

                SaveToLibraryOutcome.NotReadable -> saveFailed()
                // Saved elsewhere in the meantime; the action is already gone.
                SaveToLibraryOutcome.NothingToSave -> Unit
            }
        }
    }

    private fun saveFailed() {
        setState { copy(isSavingToLibrary = false) }
        sendUiEvent(
            UiEvent.ShowMessage(
                stringProvider.get(R.string.save_to_library_failed),
                NotificationType.Error,
            )
        )
    }

    private fun handOff(action: (ContentRef) -> Unit) {
        val document = openDocument() ?: return
        if (!currentState.canHandOff) return

        runCatching { action(ContentRef(document.uri)) }
            .onFailure {
                sendUiEvent(
                    UiEvent.ShowMessage(
                        stringProvider.get(DesignsystemR.string.issue_sharing_doc),
                        NotificationType.Error,
                    )
                )
            }
    }

    private fun print() {
        val document = openDocument() ?: return
        sendEffect(
            PdfViewerEffect.Print(
                document = ContentRef(document.uri),
                jobName = document.title ?: document.filename,
            )
        )
    }

    /**
     * What a tap on a link does (D3): an external link is previewed by its real destination, never
     * opened directly; an internal one is followed at once.
     */
    private fun tapLink(intent: PdfViewerIntent.TapLink) {
        when (val action = resolveLink(intent.link, intent.pageCount)) {
            is LinkAction.GoTo -> {
                setState { copy(linkPreview = null) }
                sendEffect(PdfViewerEffect.GoToPage(action.pageIndex, action.position, intent.page))
            }

            else -> {
                val area = intent.link.bounds.reduce(NormalizedRect::union)
                setState {
                    copy(linkPreview = LinkPreview(intent.page, area, action), selection = null)
                }
            }
        }
    }

    private fun openPreviewedLink() {
        val preview = currentState.linkPreview ?: return
        setState { copy(linkPreview = null) }
        if (preview.canOpen) sendEffect(PdfViewerEffect.OpenLink(preview.action))
    }

    private fun copyPreviewedLink() {
        val preview = currentState.linkPreview ?: return
        setState { copy(linkPreview = null) }
        preview.copyable?.let { sendEffect(PdfViewerEffect.CopyText(it)) }
    }

    /**
     * Reads the text of [visible] and the page either side, visible first, and drops the rest.
     * Pages the selection ends on are kept for its handles. A newer call cancels an older one, so a
     * fling reads only where it stops.
     */
    private fun loadTextNear(visible: IntRange) {
        if (visible.isEmpty()) return
        val wanted =
            (visible + listOf(visible.first - 1, visible.last + 1)).filter { it >= 0 }.distinct()
        loadingPages?.cancel()
        loadingPages = launch {
            setState {
                val keep = wanted + listOfNotNull(selection?.start?.page, selection?.end?.page)
                copy(
                    pageText = pageText.filterKeys { it in keep },
                    pageLinks = pageLinks.filterKeys { it in wanted },
                )
            }
            for (page in wanted) {
                if (page in currentState.pageText) continue
                load(page) ?: return@launch
            }
        }
    }

    /**
     * Reads [page] and keeps its text and links together: a page already kept is never read again,
     * so keeping one without the other would lose it. `null` when there is no document.
     */
    private suspend fun load(page: Int): PageTextState? {
        val content = session()?.page(page) ?: return null
        val text = content.toTextState()
        // A failure is not kept, so it is retried.
        if (text != PageTextState.Failed) {
            val links = (content as? PageContentResult.Available)?.links.orEmpty()
            setState {
                copy(pageText = pageText + (page to text), pageLinks = pageLinks + (page to links))
            }
        }
        return text
    }

    private fun selectAll() {
        val selection = currentState.selection ?: return
        launch {
            val lengths = selection.pages.associateWith { textOf(it)?.text?.length }
            setState {
                copy(selection = SelectionInteraction.selectAll(selection) { lengths[it] })
            }
        }
    }

    /** Copies the selected text, reading pages that are no longer on screen if needed. */
    private fun copySelection() {
        val selection = currentState.selection ?: return
        launch {
            val pages = selection.pages.associateWith { textOf(it) }
            val text = selection.text { pages[it] }
            if (text.isNotEmpty()) sendEffect(PdfViewerEffect.CopyText(text))
            setState { copy(selection = null) }
        }
    }

    private suspend fun textOf(page: Int): TextSelection? =
        (currentState.pageText[page] as? PageTextState.Text)?.selection
            ?: (session()?.page(page)?.toTextState() as? PageTextState.Text)?.selection

    /** Where a search begins: the reader's page, or the match they were last on. */
    private data class SearchStart(val page: Int, val offset: Int)

    /**
     * Looks for [query] in the whole document, starting from [fromPage]. Reading a page costs far
     * more than searching it, so:
     * - pages are read from the reader's onward, then from the top, to find the first match fast;
     * - what is read is kept folded in [searchable] and never read twice;
     * - nothing new is read until the reader pauses typing.
     *
     * The query already searched changes nothing, so the screen can report the field's text on
     * every composition.
     */
    private fun search(query: String, fromPage: Int) {
        if (pageCount == 0) {
            // The screen may report a query before the page count: it is searched once known.
            waitingSearch = query to fromPage
            return
        }
        if (currentState.search?.query == query) return
        searchJob?.cancel()

        val needle = TextSearch.needle(query)
        if (needle == null) {
            revealJob?.cancel()
            // Stored empty so the same query is not searched twice.
            setState {
                copy(
                    search =
                        DocumentSearch(
                            query = query,
                            scannedPages = pageCount,
                            pageCount = pageCount,
                        )
                )
            }
            return
        }

        val previous = currentState.search
        // Typing on stays on the current match; a first query starts at the reader's page.
        val start =
            previous?.active?.let { ref ->
                previous.matches.spanOf(ref)?.let { SearchStart(ref.page, it.start) }
            } ?: SearchStart(fromPage.coerceIn(0, pageCount - 1), offset = 0)
        // The previous matches stay until this query has its own, so highlights do not blink.
        setState {
            copy(
                search =
                    (previous ?: DocumentSearch(query)).copy(
                        query = query,
                        scannedPages = 0,
                        pageCount = pageCount,
                        unsearchable = null,
                    )
            )
        }
        searchJob = launch { scan(needle, start) }
    }

    private suspend fun scan(needle: String, start: SearchStart) {
        var matches = SearchMatches.None
        var scanned = 0
        var withText = 0
        var failed = 0
        var needsActive = true

        // Whether the state still holds the previous query's matches, until the first publish.
        var fresh = true

        fun publish(chosen: MatchRef? = null, unsearchable: TextUnavailable? = null) {
            setState {
                val current = search ?: return@setState this
                val active = chosen ?: current.active.takeUnless { fresh }
                copy(
                    search =
                        current.copy(
                            matches = matches,
                            active = active,
                            activeNumber = active?.let { matches.numberOf(it) } ?: 0,
                            scannedPages = scanned,
                            unsearchable = unsearchable,
                        )
                )
            }
            fresh = false
            if (chosen != null) reveal(chosen)
        }

        // The reader may have stepped to a match while pages were still being read.
        fun mayChoose() = fresh || currentState.search?.active == null

        var settled = false
        for (page in readingOrder(start.page, pageCount)) {
            var folded = searchable[page]
            val wasRead = folded == null
            if (folded == null) {
                if (!settled) {
                    // Show the cached pages' matches while the reader finishes typing.
                    publish()
                    delay(SEARCH_SETTLE_MS.milliseconds)
                    settled = true
                }
                folded =
                    when (val text = session()?.page(page)?.toTextState() ?: break) {
                        is PageTextState.Text -> FoldedText.of(text.selection.text)
                        PageTextState.NoText -> FoldedText.Empty
                        PageTextState.Unsupported -> {
                            unreadablePages += page
                            FoldedText.Empty
                        }

                        PageTextState.Failed -> null
                    }
                if (folded != null) searchable[page] = folded else failed++
            }
            scanned++
            if (folded != null) {
                if (!folded.isEmpty) withText++
                matches = matches.with(page, TextSearch.find(folded, needle))
            }

            var chosen: MatchRef? = null
            if (needsActive && !matches.isEmpty) {
                if (!mayChoose()) {
                    needsActive = false
                } else {
                    chosen = matches.firstFrom(start.page, start.offset)
                    if (chosen != null) needsActive = false
                }
            }
            if (chosen != null || wasRead || scanned % PUBLISH_EVERY == 0) publish(chosen)
        }

        // Nothing at or after the start: wrap to the first match.
        val wrapped = if (needsActive && mayChoose()) matches.first() else null
        scanned = pageCount
        val unsearchable =
            if (matches.isEmpty && withText == 0 && failed == 0) {
                if (unreadablePages.isEmpty()) TextUnavailable.ImageOnly
                else TextUnavailable.UnsupportedDevice
            } else null
        publish(wrapped, unsearchable)
    }

    /** Search reading order: from [first] to the end, then from the top. */
    private fun readingOrder(first: Int, count: Int): Sequence<Int> =
        (first until count).asSequence() + (0 until first).asSequence()

    /** Moves to the next or previous match, wrapping at either end. */
    private fun step(forward: Boolean) {
        val current = currentState.search ?: return
        val target =
            (if (forward) current.matches.next(current.active)
            else current.matches.previous(current.active)) ?: return
        setState {
            copy(
                search =
                    search?.let {
                        it.copy(active = target, activeNumber = it.matches.numberOf(target))
                    }
            )
        }
        reveal(target)
    }

    /**
     * Asks the screen to bring [match] into view. Locating it needs the page's geometry, which may
     * have to be read first, so a newer request replaces one still waiting.
     */
    private fun reveal(match: MatchRef) {
        revealJob?.cancel()
        revealJob = launch {
            val span = currentState.search?.matches?.spanOf(match) ?: return@launch
            val text =
                (currentState.pageText[match.page] as? PageTextState.Text)
                    ?: (load(match.page) as? PageTextState.Text)
                    ?: return@launch
            // For a match crossing lines, the first box is where the eye should land.
            val area = text.selection.highlightRects(span).firstOrNull() ?: return@launch
            sendEffect(PdfViewerEffect.RevealMatch(match.page, area))
        }
    }

    private fun closeSearch() {
        waitingSearch = null
        searchJob?.cancel()
        revealJob?.cancel()
        searchable.clear()
        unreadablePages.clear()
        setState { copy(search = null) }
    }

    private fun tellOnce(reason: TextUnavailable) {
        if (!toldAbout.add(reason)) return
        val message =
            when (reason) {
                TextUnavailable.ImageOnly -> R.string.viewer_no_text_image
                TextUnavailable.UnsupportedDevice -> R.string.viewer_no_text_unsupported
            }
        sendUiEvent(UiEvent.ShowMessage(stringProvider.get(message), NotificationType.Info))
    }

    /** The content session on the document on screen; `null` until there is one. */
    private suspend fun session(): PageContentSession? {
        val document = openDocument() ?: return null
        val session =
            contentSession
                ?: viewModelScope
                    .async {
                        contentProvider.open(DocumentSource(document.uri)).also {
                            openedSession = it
                        }
                    }
                    .also { contentSession = it }
        return session.await()
    }

    /** The session once open, to close with the ViewModel. */
    private var openedSession: PageContentSession? = null

    override fun onCleared() {
        contentSession?.cancel()
        openedSession?.close()
    }

    private fun openDocument(): BasicDocument? =
        (currentState.document as? ViewerDocumentState.Open)?.document

    private fun logSettingChanged(name: String, value: String) {
        analyticsHelper.logEvent(
            AnalyticsEvent(
                type = AnalyticsEvent.Types.PDF_VIEWER_SETTING_CHANGED,
                extras =
                    listOf(
                        AnalyticsEvent.Param(AnalyticsEvent.ParamKeys.SETTING_NAME, name),
                        AnalyticsEvent.Param(AnalyticsEvent.ParamKeys.SETTING_VALUE, value),
                    ),
            )
        )
    }

    private companion object {
        const val SCREEN_NAME = "PdfViewer"
        const val KEY_FIT_MODE = "viewer_fit_mode"
        const val KEY_NIGHT_MODE = "viewer_night_mode"

        /** How long the reader must stop typing before the pages not yet read are read. */
        const val SEARCH_SETTLE_MS = 250L

        /** How many already-read pages are searched between two updates of what is shown. */
        const val PUBLISH_EVERY = 16
    }
}
