/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.search

import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SearchResult

/**
 * @property results the documents matching [resultsFor], best match first, each with where in its
 *   text it matched when that is where it was found.
 * @property resultsFor the query [results] answer, which trails [query] while the user types. Only
 *   when the two agree does an empty [results] mean nothing matches, rather than not searched yet.
 * @property notFoundUuids the documents whose file is not there, which are shown saying so.
 */
data class DocumentSearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val resultsFor: String? = null,
    val notFoundUuids: Set<String> = emptySet(),
) {
    val hasNoMatches: Boolean
        get() = query.isNotBlank() && resultsFor == query && results.isEmpty()
}

sealed interface DocumentSearchIntent {
    data class UpdateQuery(val query: String) : DocumentSearchIntent

    data object ClearQuery : DocumentSearchIntent
}
