/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.search

import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument

/**
 * @property results the documents matching [resultsFor].
 * @property resultsFor the query [results] answer, which trails [query] while the user types. Only
 *   when the two agree does an empty [results] mean nothing matches, rather than not searched yet.
 */
data class DocumentSearchUiState(
    val query: String = "",
    val results: List<ScannedDocument> = emptyList(),
    val resultsFor: String? = null,
) {
    val hasNoMatches: Boolean
        get() = query.isNotBlank() && resultsFor == query && results.isEmpty()
}

sealed interface DocumentSearchIntent {
    data class UpdateQuery(val query: String) : DocumentSearchIntent

    data object ClearQuery : DocumentSearchIntent
}
