/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.domain.StringProvider
import com.bobbyesp.docucraft.core.domain.analytics.AnalyticsEvent
import com.bobbyesp.docucraft.core.domain.notifications.NotificationType
import com.bobbyesp.docucraft.core.domain.repository.AnalyticsHelper
import com.bobbyesp.docucraft.core.util.events.UiEvent
import com.bobbyesp.docucraft.core.util.viewModel.BaseViewModel
import com.bobbyesp.docucraft.feature.docscanner.domain.FilterOptions
import com.bobbyesp.docucraft.feature.docscanner.domain.ScanRequestBus
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ProcessDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SaveScanDraftUseCase
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeEffect
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

class HomeViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val documentScanner: DocumentScanner,
    private val scanRequests: ScanRequestBus,
    private val observeDocumentsUseCase: ObserveDocumentsUseCase,
    private val processDocumentsUseCase: ProcessDocumentsUseCase,
    private val saveScanDraftUseCase: SaveScanDraftUseCase,
    private val stringProvider: StringProvider,
    private val analyticsHelper: AnalyticsHelper,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseViewModel<HomeIntent, HomeUiState, HomeEffect>(initialState = HomeUiState()) {

    init {
        observeDocuments()
        observeExternalScanRequests()
        resumePendingScan()
    }

    // ---------------- INTENTS ----------------

    override fun onHandleIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.Load -> observeDocuments()

            HomeIntent.LaunchScanner -> startScan()

            is HomeIntent.ViewDocument -> openDocument(intent.id)

            is HomeIntent.UpdateSearch -> {
                if (intent.query.length >= 3 && intent.query != currentState.searchQuery) {
                    analyticsHelper.logEvent(
                        AnalyticsEvent(
                            type = AnalyticsEvent.Types.SEARCH_PERFORMED,
                            extras =
                                listOf(
                                    AnalyticsEvent.Param(
                                        AnalyticsEvent.ParamKeys.QUERY_LENGTH,
                                        intent.query.length.toString(),
                                    )
                                ),
                        )
                    )
                }
                setState { copy(searchQuery = intent.query) }
            }

            HomeIntent.ClearSearch -> setState { copy(searchQuery = "") }

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

            HomeIntent.ClearFilters -> setState { copy(filterOptions = FilterOptions.default) }

            is HomeIntent.OpenActions -> sendEffect(HomeEffect.OpenDocumentActions(intent.id))

            HomeIntent.OpenSettings -> sendEffect(HomeEffect.OpenSettings)
        }
    }

    // ---------------- OBSERVE ----------------

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private fun observeDocuments() = launch {
        combine(
                observeDocumentsUseCase(),
                state.map { it.searchQuery }.debounce(150).distinctUntilChanged(),
                state.map { it.filterOptions }.distinctUntilChanged(),
            ) { docs, query, filters ->
                Triple(docs, query, filters)
            }
            .mapLatest { (docs, query, filters) ->
                val processed =
                    withContext(defaultDispatcher) {
                        processDocumentsUseCase(docs, query, filters, filters.sortBy)
                    }

                processed to docs.isNotEmpty()
            }
            .onStart { setState { copy(status = HomeStatus.Loading) } }
            .catch { error ->
                val message = stringProvider.getError(error)

                setState { copy(status = HomeStatus.Error(message)) }

                sendUiEvent(UiEvent.ShowMessage(message, NotificationType.Error))
            }
            .collect { (sorted, hasDocuments) ->
                setState {
                    copy(
                        visibleDocuments = sorted,
                        hasDocuments = hasDocuments,
                        status = HomeStatus.Idle,
                    )
                }
            }
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
            .onSuccess {
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

                sendUiEvent(
                    UiEvent.ShowMessage(
                        stringProvider.get(R.string.doc_saved_successfully),
                        NotificationType.Success,
                    )
                )
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
    private fun openDocument(uuid: String) = sendEffect(HomeEffect.OpenDocument(uuid))

    private companion object {
        const val KEY_SCAN_IN_FLIGHT = "scan_in_flight"
    }
}
