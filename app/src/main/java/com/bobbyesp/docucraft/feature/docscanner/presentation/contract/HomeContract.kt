package com.bobbyesp.docucraft.feature.docscanner.presentation.contract

import android.net.Uri
import com.bobbyesp.docucraft.core.util.state.TemporalState
import com.bobbyesp.docucraft.feature.docscanner.domain.FilterOptions
import com.bobbyesp.docucraft.feature.docscanner.domain.SortOption
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.mlkit.domain.model.Document

/**
 * Defines the contract for the Home Screen (MVI Pattern). Contains the State, Actions (Inputs), and
 * Effects (Outputs).
 */
enum class PageContentState {
    LOADING,
    ERROR,
    EMPTY,
    SUCCESS,
}

sealed interface LoadState {
    data object Idle : LoadState

    data object Loading : LoadState

    data class Error(val message: String) : LoadState
}

// --- STATE ---
data class HomeUiState(
    // Global Loading & Error
    val fetchState: LoadState = LoadState.Loading,

    // Data
    val scannedDocuments: List<ScannedDocument> = emptyList(),
    val hasDocuments: Boolean = true,

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
        get() = (fetchState as? LoadState.Error)?.message

    val hasActiveFilters: Boolean
        get() =
            filterOptions.minPageCount != null ||
                filterOptions.minFileSize != null ||
                filterOptions.dateRange != null ||
                filterOptions.sortBy != SortOption.DateDesc

    val isEmptyResult: Boolean
        get() = scannedDocuments.isEmpty() && hasDocuments
}

// --- ACTIONS (Inputs from UI) ---
sealed interface HomeUiAction {
    // Scanning
    data object OnScanButtonClicked : HomeUiAction

    data class OnScanResultReceived(val result: Any) : HomeUiAction

    data object OnScanErrorDismissed : HomeUiAction

    // Document Operations (User Intents)
    data class OpenDocument(val uri: Uri) : HomeUiAction

    data class SaveDocument(val document: ScannedDocument) : HomeUiAction

    data class ShareDocument(val uri: Uri) : HomeUiAction

    data class DeleteDocument(val id: String?) : HomeUiAction // null id means cancel/dismiss

    data class UpdateDocumentFields(val id: String, val title: String, val description: String) :
        HomeUiAction

    // Dialog Triggers
    data class ShowDeleteConfirmation(val id: String) : HomeUiAction

    data class ShowEditDialog(val id: String) : HomeUiAction

    data class ShowDocumentInfo(val id: String) : HomeUiAction

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
    // Generic
    data class ShowError(val error: Throwable) : HomeUiEffect

    data class ShowMessage(val message: String) : HomeUiEffect

    // Operation Results
    data object LaunchScanner : HomeUiEffect

    data object ScanSuccess : HomeUiEffect

    data class ScanFailure(val error: Throwable) : HomeUiEffect

    data class SaveSuccess(val uri: Uri) : HomeUiEffect

    data class SaveFailure(val error: Throwable) : HomeUiEffect

    data object DeleteSuccess : HomeUiEffect

    data class DeleteFailure(val error: Throwable) : HomeUiEffect

    data class OpenDocumentViewerFailure(val error: Throwable) : HomeUiEffect

    data class ShareDocumentFailure(val error: Throwable) : HomeUiEffect

    data class ShowDocumentInformationDialog(val id: String) : HomeUiEffect
}
