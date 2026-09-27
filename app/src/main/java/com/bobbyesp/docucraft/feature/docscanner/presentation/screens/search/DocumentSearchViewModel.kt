/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.search

import androidx.lifecycle.SavedStateHandle
import com.bobbyesp.docucraft.core.domain.StringProvider
import com.bobbyesp.docucraft.core.domain.analytics.AnalyticsEvent
import com.bobbyesp.docucraft.core.domain.notifications.NotificationType
import com.bobbyesp.docucraft.core.domain.repository.AnalyticsHelper
import com.bobbyesp.docucraft.core.util.events.UiEvent
import com.bobbyesp.docucraft.core.util.viewModel.BaseViewModel
import com.bobbyesp.docucraft.feature.docscanner.domain.FilterOptions
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ProcessDocumentsUseCase
import kotlin.time.Duration.Companion.milliseconds
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
import kotlinx.coroutines.withContext

/**
 * Searches the catalogue as the user types, and keeps up with it while they do: a document scanned,
 * renamed or deleted meanwhile changes the results without the query being typed again.
 *
 * The query is kept in [savedStateHandle], so a search the system killed the process under comes
 * back as the user left it.
 */
class DocumentSearchViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val observeDocumentsUseCase: ObserveDocumentsUseCase,
    private val processDocumentsUseCase: ProcessDocumentsUseCase,
    private val stringProvider: StringProvider,
    private val analyticsHelper: AnalyticsHelper,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) :
    BaseViewModel<DocumentSearchIntent, DocumentSearchUiState, Nothing>(
        initialState = DocumentSearchUiState(query = savedStateHandle[KEY_QUERY] ?: "")
    ) {

    init {
        observeResults()
    }

    override fun onHandleIntent(intent: DocumentSearchIntent) {
        when (intent) {
            is DocumentSearchIntent.UpdateQuery -> updateQuery(intent.query)
            DocumentSearchIntent.ClearQuery -> updateQuery("")
        }
    }

    private fun updateQuery(query: String) {
        if (query == currentState.query) return

        if (query.length >= 3) {
            analyticsHelper.logEvent(
                AnalyticsEvent(
                    type = AnalyticsEvent.Types.SEARCH_PERFORMED,
                    extras =
                        listOf(
                            AnalyticsEvent.Param(
                                AnalyticsEvent.ParamKeys.QUERY_LENGTH,
                                query.length.toString(),
                            )
                        ),
                )
            )
        }

        savedStateHandle[KEY_QUERY] = query
        setState { copy(query = query) }
    }

    /**
     * An empty query answers with nothing rather than with the whole catalogue: that is Home's job,
     * and the screen shows what can be searched for instead.
     */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private fun observeResults() = launch {
        combine(
                observeDocumentsUseCase(),
                state.map { it.query }.debounce(150.milliseconds).distinctUntilChanged(),
            ) { documents, query ->
                documents to query
            }
            .mapLatest { (documents, query) ->
                val results =
                    if (query.isBlank()) {
                        emptyList()
                    } else {
                        withContext(defaultDispatcher) {
                            processDocumentsUseCase(
                                documents,
                                query,
                                FilterOptions.default,
                                FilterOptions.default.sortBy,
                            )
                        }
                    }
                results to query
            }
            .catch { error ->
                sendUiEvent(
                    UiEvent.ShowMessage(stringProvider.getError(error), NotificationType.Error)
                )
            }
            .collect { (results, query) ->
                setState { copy(results = results, resultsFor = query) }
            }
    }

    private companion object {
        const val KEY_QUERY = "query"
    }
}
