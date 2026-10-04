/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.domain.StringProvider
import com.bobbyesp.docucraft.core.domain.analytics.AnalyticsEvent
import com.bobbyesp.docucraft.core.domain.notifications.NotificationType
import com.bobbyesp.docucraft.core.domain.preferences.SettingsRepository
import com.bobbyesp.docucraft.core.domain.repository.AnalyticsHelper
import com.bobbyesp.docucraft.core.util.events.UiEvent
import com.bobbyesp.docucraft.core.util.viewModel.BaseViewModel
import com.bobbyesp.docucraft.feature.docscanner.domain.FilterOptions
import com.bobbyesp.docucraft.feature.docscanner.domain.ScanRequestBus
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.model.RecentDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.TagsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveHomeSectionsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveLibraryUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveNotFoundDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveRecentDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ProcessDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SaveScanDraftUseCase
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeIntent
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeStatus
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeUiState
import com.bobbyesp.scanner.DocumentScanner
import com.bobbyesp.scanner.ScanDraft
import com.bobbyesp.scanner.ScanError
import com.bobbyesp.scanner.ScanOutcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

class HomeViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val documentScanner: DocumentScanner,
    private val scanRequests: ScanRequestBus,
    private val observeDocumentsUseCase: ObserveDocumentsUseCase,
    private val observeRecentDocumentsUseCase: ObserveRecentDocumentsUseCase,
    private val observeLibraryUseCase: ObserveLibraryUseCase,
    private val observeHomeSectionsUseCase: ObserveHomeSectionsUseCase,
    private val tags: TagsRepository,
    private val observeNotFoundDocuments: ObserveNotFoundDocumentsUseCase,
    private val processDocumentsUseCase: ProcessDocumentsUseCase,
    private val saveScanDraftUseCase: SaveScanDraftUseCase,
    private val settings: SettingsRepository,
    private val stringProvider: StringProvider,
    private val analyticsHelper: AnalyticsHelper,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) :
    BaseViewModel<HomeIntent, HomeUiState, Nothing>(
        initialState = HomeUiState(scanToReview = savedStateHandle[KEY_SCAN_TO_REVIEW])
    ) {
    // `Nothing` because this raises no effects: everything it is asked to do, it does.

    init {
        observeDocuments()
        observeOrganization()
        observeExternalScanRequests()
        resumePendingScan()
    }

    // ---------------- INTENTS ----------------

    override fun onHandleIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.Load -> observeDocuments()

            HomeIntent.LaunchScanner -> startScan()

            HomeIntent.ScanReviewOpened -> {
                savedStateHandle[KEY_SCAN_TO_REVIEW] = null
                setState { copy(scanToReview = null) }
            }

            is HomeIntent.ApplySort -> {
                analyticsHelper.logEvent(
                    AnalyticsEvent(
                        type = AnalyticsEvent.Types.FILTER_APPLIED,
                        extras =
                            listOf(
                                AnalyticsEvent.Param(
                                    AnalyticsEvent.ParamKeys.SORT_BY,
                                    "${intent.sort.criteria.name}_${intent.sort.order.name}",
                                )
                            ),
                    )
                )
                setState { copy(filterOptions = filterOptions.copy(sortBy = intent.sort)) }
            }

            is HomeIntent.ApplyFilter -> {
                analyticsHelper.logEvent(
                    AnalyticsEvent(
                        type = AnalyticsEvent.Types.FILTER_APPLIED,
                        extras =
                            listOf(
                                AnalyticsEvent.Param(
                                    AnalyticsEvent.ParamKeys.FILTER_TYPE,
                                    "complex_filter",
                                )
                            ),
                    )
                )
                setState { copy(filterOptions = intent.filter) }
            }

            HomeIntent.ToggleFavoritesFilter ->
                setState {
                    copy(
                        filterOptions =
                            filterOptions.copy(favoritesOnly = !filterOptions.favoritesOnly)
                    )
                }

            is HomeIntent.ToggleTagFilter ->
                setState {
                    val selected = filterOptions.tagUuids
                    copy(
                        filterOptions =
                            filterOptions.copy(
                                tagUuids =
                                    if (intent.tagUuid in selected) selected - intent.tagUuid
                                    else selected + intent.tagUuid
                            )
                    )
                }

            is HomeIntent.ShowOnlyTag ->
                setState {
                    copy(
                        filterOptions =
                            filterOptions.copy(
                                favoritesOnly = false,
                                tagUuids = setOf(intent.tagUuid),
                            )
                    )
                }

            // The order is not a filter: clearing them leaves the list sorted as it was.
            HomeIntent.ClearFilters ->
                setState {
                    copy(filterOptions = FilterOptions.default.copy(sortBy = filterOptions.sortBy))
                }
        }
    }

    // ---------------- OBSERVE ----------------

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeDocuments() = launch {
        // What the list is narrowed down from: the whole library, or what carries the chosen
        // tags. Asked again only when the tags change, not on every change of order.
        val narrowed =
            state
                .map { it.filterOptions.tagUuids }
                .distinctUntilChanged()
                .flatMapLatest { tagUuids -> observeLibraryUseCase(tagUuids) }

        combine(
                observeDocumentsUseCase(),
                narrowed,
                observeRecentDocumentsUseCase(limit = RECENTS_SHOWN),
                state.map { it.filterOptions }.distinctUntilChanged(),
            ) { library, shown, recents, filters ->
                LibrarySnapshot(library, shown, recents, filters)
            }
            .mapLatest { (library, shown, recents, filters) ->
                val processed =
                    withContext(defaultDispatcher) {
                        processDocumentsUseCase(shown, filters, filters.sortBy)
                    }

                Triple(processed, shelfOf(recents, library = library), library.isNotEmpty())
            }
            .onStart { setState { copy(status = HomeStatus.Loading) } }
            .catch { error ->
                val message = stringProvider.getError(error)

                setState { copy(status = HomeStatus.Error(message)) }

                sendUiEvent(UiEvent.ShowMessage(message, NotificationType.Error))
            }
            .collect { (sorted, recent, hasDocuments) ->
                setState {
                    copy(
                        visibleDocuments = sorted,
                        recentDocuments = recent,
                        hasDocuments = hasDocuments,
                        status = HomeStatus.Idle,
                    )
                }
            }
    }

    private data class LibrarySnapshot(
        val library: List<Document.Managed>,
        val shown: List<Document.Managed>,
        val recents: List<RecentDocument>,
        val filters: FilterOptions,
    )

    /**
     * How the library is organized: the pinned folders and the sections of the tags, and every tag
     * for the list to be narrowed down by. On its own, so that a tag renamed or a folder pinned
     * does not sort the documents again.
     */
    private fun observeOrganization() {
        launch {
            observeHomeSectionsUseCase().collect { sections ->
                setState {
                    copy(
                        pinnedFolders = sections.pinnedFolders,
                        tagSections = sections.tagSections,
                    )
                }
            }
        }
        launch {
            observeNotFoundDocuments().collect { notFound ->
                setState { copy(notFoundUuids = notFound) }
            }
        }
        launch {
            tags.observeTags().collect { all ->
                setState {
                    // A tag that was deleted cannot go on narrowing the list down.
                    val known = all.mapTo(HashSet()) { it.uuid }
                    copy(
                        tags = all,
                        filterOptions =
                            filterOptions.copy(
                                tagUuids =
                                    filterOptions.tagUuids.filterTo(HashSet()) { it in known }
                            ),
                    )
                }
            }
        }
    }

    /**
     * What the Recents shelf shows: the documents used last, whatever order the list below is
     * sorted in.
     *
     * With [RECENTS_MINIMUM] or fewer documents in the library, the list shows every one of them at
     * a glance, and the shelf would only repeat it. Unless it holds a document of another app:
     * those are not in the list, and the shelf is the only place they can be found.
     */
    private fun shelfOf(
        recents: List<RecentDocument>,
        library: List<Document.Managed>,
    ): List<RecentDocument> {
        val repeatsTheList =
            library.size <= RECENTS_MINIMUM && recents.all { it.document is Document.Managed }
        return if (repeatsTheList) emptyList() else recents
    }

    /**
     * Entry points outside the UI, such as the home screen widget.
     *
     * The request stands until taken, so one made while the catalogue was off screen is honoured as
     * soon as it comes back — which the shell arranges. Taking it is what stops it being acted on
     * twice.
     */
    private fun observeExternalScanRequests() = launch {
        scanRequests.isPending.collect { pending ->
            if (pending && scanRequests.take()) startScan()
        }
    }

    // ---------------- SCANNING ----------------

    /**
     * Runs a scan start to finish. The whole session lives in [viewModelScope], so it survives the
     * scanner covering the app and the Activity being recreated underneath it.
     */
    private fun startScan() {
        if (currentState.isScanning) return

        beginScan()

        launch(onError = { endScan() }) {
            analyticsHelper.logEvent(AnalyticsEvent(AnalyticsEvent.Types.SCAN_STARTED))

            val outcome = documentScanner.scan()
            endScan()
            handle(outcome)
        }
    }

    /**
     * Rejoins a scan that was running when this process was killed for memory.
     *
     * The scanner runs elsewhere and keeps going, so the user may well have finished a document
     * that the app then threw away on the way back. The flag rides in [savedStateHandle], which
     * survives process death; [DocumentScanner.resumePendingScan] returns null when it turns out
     * nothing was owed after all.
     */
    private fun resumePendingScan() {
        if (savedStateHandle.get<Boolean>(KEY_SCAN_IN_FLIGHT) != true) return

        setState { copy(isScanning = true) }

        launch(onError = { endScan() }) {
            val outcome = documentScanner.resumePendingScan()
            endScan()
            outcome?.let { handle(it) }
        }
    }

    private suspend fun handle(outcome: ScanOutcome) {
        when (outcome) {
            is ScanOutcome.Completed -> onScanCompleted(outcome.draft)
            ScanOutcome.Cancelled -> onScanCancelled()
            is ScanOutcome.Failed -> onScanFailed(outcome.error)
        }
    }

    private fun beginScan() {
        savedStateHandle[KEY_SCAN_IN_FLIGHT] = true
        setState { copy(isScanning = true) }
    }

    private fun endScan() {
        savedStateHandle[KEY_SCAN_IN_FLIGHT] = false
        setState { copy(isScanning = false) }
    }

    /**
     * The use case reports failure through its [Result] rather than by throwing, so the outcome has
     * to be read: ignoring it used to congratulate the user on a save that never happened.
     */
    private suspend fun onScanCompleted(draft: ScanDraft) {
        if (draft.pdf == null) return onScanFailed(ScanError.NoOutputProduced)

        saveScanDraftUseCase(draft)
            .onSuccess { uuid ->
                analyticsHelper.logEvent(
                    AnalyticsEvent(
                        type = AnalyticsEvent.Types.SCAN_COMPLETED,
                        extras =
                            listOf(
                                AnalyticsEvent.Param(
                                    AnalyticsEvent.ParamKeys.PAGE_COUNT,
                                    draft.pdf?.pageCount.toString(),
                                )
                            ),
                    )
                )

                // The review is what tells the user the scan is saved. Without it, a message does.
                // Kept where a process death does not lose it: the scanner outlives the process,
                // and the scan it hands back then is as much to be reviewed as any other.
                if (settings.settings.first().reviewNewScans) {
                    savedStateHandle[KEY_SCAN_TO_REVIEW] = uuid
                    setState { copy(scanToReview = uuid) }
                } else {
                    sendUiEvent(
                        UiEvent.ShowMessage(
                            stringProvider.get(R.string.doc_saved_successfully),
                            NotificationType.Success,
                        )
                    )
                }
            }
            .onFailure { error ->
                sendUiEvent(
                    UiEvent.ShowMessage(stringProvider.getError(error), NotificationType.Error)
                )
            }
    }

    /** Walking away from the scanner is an ordinary outcome: record it, say nothing. */
    private fun onScanCancelled() {
        analyticsHelper.logEvent(AnalyticsEvent(type = AnalyticsEvent.Types.SCAN_CANCELLED))
    }

    /** A real failure, unlike a cancellation, is worth both an event and a word to the user. */
    private fun onScanFailed(error: ScanError) {
        analyticsHelper.logEvent(
            AnalyticsEvent(
                type = AnalyticsEvent.Types.SCAN_FAILED,
                extras =
                    listOf(AnalyticsEvent.Param(AnalyticsEvent.ParamKeys.STATUS, error.describe())),
            )
        )

        val message =
            when (error) {
                is ScanError.Engine -> stringProvider.getError(error.cause)
                else -> stringProvider.get(R.string.unknown_error)
            }

        sendUiEvent(UiEvent.ShowMessage(message, NotificationType.Error))
    }

    private fun ScanError.describe(): String =
        when (this) {
            ScanError.EngineUnavailable -> "engine_unavailable"
            ScanError.PermissionDenied -> "permission_denied"
            ScanError.NoOutputProduced -> "no_output_produced"
            is ScanError.Engine -> cause.message ?: "engine_error"
        }

    // ---------------- ACTIONS ----------------

    /** The viewer reads the document itself; all it needs from here is which one. */
    private companion object {
        const val KEY_SCAN_IN_FLIGHT = "scan_in_flight"
        const val KEY_SCAN_TO_REVIEW = "scan_to_review"
        const val RECENTS_MINIMUM = 3
        const val RECENTS_SHOWN = 8
    }
}
