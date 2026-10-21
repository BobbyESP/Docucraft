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
 *
 * Nor is searching: that is the search screen's, with a state holder of its own.
 */
sealed interface HomeIntent {
    data object Load : HomeIntent

    data object LaunchScanner : HomeIntent

    /** The review of the scan that was waiting for one is on screen, so it waits no longer. */
    data object ScanReviewOpened : HomeIntent

    data class ApplySort(val sort: SortOption) : HomeIntent

    data class ApplyFilter(val filter: FilterOptions) : HomeIntent

    /** Show only the favorites in the list of documents, or every document again. */
    data object ToggleFavoritesFilter : HomeIntent

    /** Narrow the list of documents down to those with this tag, or stop doing so. */
    data class ToggleTagFilter(val tagUuid: String) : HomeIntent

    /** Show only the documents with this tag: what a tag's section offers to see all of. */
    data class ShowOnlyTag(val tagUuid: String) : HomeIntent

    data object ClearFilters : HomeIntent
}
