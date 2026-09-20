/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.contract

import com.bobbyesp.docucraft.feature.docscanner.domain.FilterOptions
import com.bobbyesp.docucraft.feature.docscanner.domain.SortOption

sealed interface HomeIntent {
    data object Load : HomeIntent

    data object LaunchScanner : HomeIntent

    data object OpenSettings : HomeIntent

    data class ViewDocument(val id: String) : HomeIntent

    data class UpdateSearch(val query: String) : HomeIntent

    data object ClearSearch : HomeIntent

    data class ApplySort(val sort: SortOption) : HomeIntent

    data class ApplyFilter(val filter: FilterOptions) : HomeIntent

    data object ClearFilters : HomeIntent

    /** Acting on a document, which is a destination of its own rather than state held here. */
    data class OpenActions(val id: String) : HomeIntent
}
