/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.contract

import com.bobbyesp.docucraft.feature.docscanner.domain.FilterOptions
import com.bobbyesp.docucraft.feature.docscanner.domain.SortOption

/**
 * What the catalogue can be asked to *do*. Going somewhere is not on the list: navigation is not
 * state this holds, nor work it performs, so routing a tap through here bought nothing and cost a
 * queue that could replay it later against a screen the user had since left.
 */
sealed interface HomeIntent {
    data object Load : HomeIntent

    data object LaunchScanner : HomeIntent

    data class UpdateSearch(val query: String) : HomeIntent

    data object ClearSearch : HomeIntent

    data class ApplySort(val sort: SortOption) : HomeIntent

    data class ApplyFilter(val filter: FilterOptions) : HomeIntent

    data object ClearFilters : HomeIntent
}
