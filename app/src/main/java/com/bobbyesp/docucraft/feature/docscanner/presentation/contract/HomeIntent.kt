package com.bobbyesp.docucraft.feature.docscanner.presentation.contract

import com.bobbyesp.docucraft.feature.docscanner.domain.FilterOptions
import com.bobbyesp.docucraft.feature.docscanner.domain.SortOption
import com.bobbyesp.docucraft.feature.docscanner.domain.model.RawScanResult
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.sheet.SheetAction

sealed interface HomeIntent {
    data object Load : HomeIntent
    data object LaunchScanner : HomeIntent

    data class ScanResult(val result: RawScanResult) : HomeIntent
    data class ViewDocument(val id: String) : HomeIntent

    data class UpdateSearch(val query: String) : HomeIntent
    data object ClearSearch : HomeIntent

    data class ToggleSearch(val visible: Boolean) : HomeIntent
    data class ApplySort(val sort: SortOption) : HomeIntent
    data class ApplyFilter(val filter: FilterOptions) : HomeIntent
    data object ClearFilters : HomeIntent

    data class OpenSheet(val id: String) : HomeIntent
    data object DismissSheet : HomeIntent

    data class Sheet(val action: SheetAction) : HomeIntent
}