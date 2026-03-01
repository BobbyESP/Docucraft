package com.bobbyesp.docucraft.feature.docscanner.presentation.contract

import com.bobbyesp.docucraft.core.presentation.common.Route
import com.bobbyesp.docucraft.feature.docscanner.domain.FilterOptions
import com.bobbyesp.docucraft.feature.docscanner.domain.SortOption
import com.bobbyesp.docucraft.feature.docscanner.domain.model.RawScanResult
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.sheet.DocumentSheetUiState

sealed interface HomeStatus {
    data object Idle : HomeStatus
    data object Loading : HomeStatus
    data class Error(val message: String) : HomeStatus
}

data class HomeUiState(
    val status: HomeStatus = HomeStatus.Loading,
    val visibleDocuments: List<ScannedDocument> = emptyList(),
    val hasDocuments: Boolean = false,
    val searchQuery: String = "",
    val isSearchBarVisible: Boolean = false,
    val filterOptions: FilterOptions = FilterOptions.default,
    val isScanning: Boolean = false,
    val mostRecentScan: RawScanResult? = null,
    /** Null means the sheet/dialog is closed. */
    val sheetState: DocumentSheetUiState? = null,
) {
    val errorMessage: String? = (status as? HomeStatus.Error)?.message

    val hasActiveFilters: Boolean = filterOptions.run {
        minPageCount != null || minFileSize != null || dateRange != null || sortBy != SortOption.DateDesc
    }

    val isEmptyResult: Boolean = visibleDocuments.isEmpty() && hasDocuments
}

// --- ACTIONS (Inputs from UI) ---
sealed interface HomeUiAction {
    // Scanning
    data object LaunchDocumentScanner : HomeUiAction
    data class ScanResultAction(val rawScanResult: RawScanResult) : HomeUiAction

    // Document viewing
    data class ViewDocument(val id: String) : HomeUiAction

    // Search & Filter
    data class UpdateSearchQuery(val query: String) : HomeUiAction
    data object ClearSearch : HomeUiAction
    data class ToggleSearchBar(val isVisible: Boolean) : HomeUiAction
    data class ApplySort(val sortOption: SortOption) : HomeUiAction
    data class ApplyFilter(val filterOptions: FilterOptions) : HomeUiAction
    data object ClearFilters : HomeUiAction

    data class OpenSheet(val documentId: String) : HomeUiAction
}

// --- EFFECTS (One-off Events for UI) ---
sealed interface HomeUiEffect {
    data class Navigate(val route: Route) : HomeUiEffect
}
