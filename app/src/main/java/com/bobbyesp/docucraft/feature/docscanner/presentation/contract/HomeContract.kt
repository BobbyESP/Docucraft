/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.contract

import com.bobbyesp.docucraft.feature.docscanner.domain.FilterOptions
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.sheet.DocumentSheetUiState

sealed interface HomeStatus {
    data object Idle : HomeStatus

    data object Loading : HomeStatus

    data class Error(val message: String) : HomeStatus
}

/**
 * What the main content area should render.
 *
 * Derived from [HomeUiState] so the screen switches on a single value instead of on a chain of
 * nested conditions, and so every state is enumerable in previews and tests.
 *
 * Note there is no "no results" entry: an empty search result is shown inside the search view,
 * where the query that produced it is still on screen.
 */
enum class HomeContentState {
    Loading,
    Error,
    EmptyLibrary,
    Documents,
}

data class HomeUiState(
    val status: HomeStatus = HomeStatus.Loading,
    val visibleDocuments: List<ScannedDocument> = emptyList(),
    val hasDocuments: Boolean = false,
    val searchQuery: String = "",
    val filterOptions: FilterOptions = FilterOptions.default,
    val isScanning: Boolean = false,
    /** Null means the sheet/dialog is closed. */
    val sheetState: DocumentSheetUiState? = null,
) {
    val errorMessage: String? = (status as? HomeStatus.Error)?.message

    /** True when a query or filter has hidden every document the library actually holds. */
    val isEmptyResult: Boolean = visibleDocuments.isEmpty() && hasDocuments

    val contentState: HomeContentState =
        when {
            status is HomeStatus.Loading -> HomeContentState.Loading
            status is HomeStatus.Error -> HomeContentState.Error
            !hasDocuments -> HomeContentState.EmptyLibrary
            else -> HomeContentState.Documents
        }
}
