package com.bobbyesp.docucraft.feature.docscanner.presentation.contract

import android.net.Uri
import com.bobbyesp.docucraft.core.presentation.common.Route
import com.bobbyesp.docucraft.core.util.state.ScreenState
import com.bobbyesp.docucraft.core.util.state.TemporalState
import com.bobbyesp.docucraft.feature.docscanner.domain.FilterOptions
import com.bobbyesp.docucraft.feature.docscanner.domain.SortOption
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.mlkit.domain.model.Document

// --- STATE ---
data class HomeUiState(
    // Global Loading & Error
    val fetchState: ScreenState<Unit> = ScreenState.Loading(),

    // Data
    val visibleDocuments: List<ScannedDocument> = emptyList(),
    val hasDocuments: Boolean = false,

    // Dialogs & Temporal States
    val documentForRemoval: TemporalState<ScannedDocument> = TemporalState.NotPresent,
    val documentForModification: TemporalState<ScannedDocument> = TemporalState.NotPresent,
    val documentInfoCandidate: TemporalState<ScannedDocument> = TemporalState.NotPresent,
    val documentForActionMenu: TemporalState<ScannedDocument> = TemporalState.NotPresent,

    // Search & Filter State
    val searchQuery: String = "",
    val isSearchBarVisible: Boolean = false,
    val filterOptions: FilterOptions = FilterOptions.default,

    // Scanning State
    val isScanning: Boolean = false,
    val mostRecentScan: Document? = null,
    val scanUserMessage: String? = null,
) {

    val errorMessage: String?
        get() = (fetchState as? ScreenState.Error)?.message

    val hasActiveFilters: Boolean
        get() =
            filterOptions.minPageCount != null ||
                filterOptions.minFileSize != null ||
                filterOptions.dateRange != null ||
                filterOptions.sortBy != SortOption.DateDesc

    val isEmptyResult: Boolean
        get() = visibleDocuments.isEmpty() && hasDocuments
}

// --- ACTIONS (Inputs from UI) ---
sealed interface HomeUiAction {
    // Scanning
    data object OnScanButtonClicked : HomeUiAction

    data class OnScanResultReceived(val result: Any) : HomeUiAction

    data object OnScanErrorDismissed : HomeUiAction

    // Document Operations (User Intents)
    data class OpenDocument(val id: String) : HomeUiAction

    data class SaveDocument(val document: ScannedDocument) : HomeUiAction

    data class ShareDocument(val uri: Uri) : HomeUiAction

    data class DeleteDocument(val id: String?) : HomeUiAction // null id means cancel/dismiss

    data class UpdateDocumentFields(val id: String, val title: String, val description: String) :
        HomeUiAction

    // Dialog Triggers
    data class ShowDeleteConfirmation(val id: String) : HomeUiAction

    data class ShowEditDialog(val id: String) : HomeUiAction

    data class ShowOptionsSheet(val id: String) : HomeUiAction

    data object DismissDialogs : HomeUiAction

    data object DismissOptionsSheet : HomeUiAction

    // Search & Filter
    data class UpdateSearchQuery(val query: String) : HomeUiAction

    data object ClearSearch : HomeUiAction

    data class ToggleSearchBar(val isVisible: Boolean) : HomeUiAction

    data class ApplySort(val sortOption: SortOption) : HomeUiAction

    data class ApplyFilter(val filterOptions: FilterOptions) : HomeUiAction

    data object ClearFilters : HomeUiAction
}

// --- EFFECTS (One-off Events for UI) ---
sealed interface HomeUiEffect {
    data class Navigate(val route: Route) : HomeUiEffect

    data object LaunchScanner : HomeUiEffect
}
