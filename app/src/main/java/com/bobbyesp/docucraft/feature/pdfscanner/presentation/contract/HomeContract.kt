package com.bobbyesp.docucraft.feature.pdfscanner.presentation.contract

import android.net.Uri
import com.bobbyesp.docucraft.core.domain.model.ScannedDocument
import com.bobbyesp.docucraft.core.util.state.TemporalState
import com.bobbyesp.docucraft.feature.pdfscanner.domain.FilterOptions
import com.bobbyesp.docucraft.feature.pdfscanner.domain.SortOption
import com.bobbyesp.docucraft.feature.pdfscanner.domain.model.ScannedPdf

/**
 * Defines the contract for the Home Screen (MVI Pattern). Contains the State, Actions (Inputs), and
 * Effects (Outputs).
 */

enum class PageContentState {
    LOADING,
    ERROR,
    EMPTY,
    SUCCESS
}

sealed interface LoadState {
    data object Idle : LoadState
    data object Loading : LoadState
    data class Error(val message: String) : LoadState
}

// --- STATE ---
data class HomeUiState(
    // Global Loading & Error
    val loadState: LoadState = LoadState.Loading,

    // Data
    val scannedPdfs: List<ScannedPdf> = emptyList(),
    val isRepositoryEmpty: Boolean = true,

    // Dialogs & Temporal States
    val pdfToBeRemoved: TemporalState<ScannedPdf> = TemporalState.NotPresent,
    val pdfToBeModified: TemporalState<ScannedPdf> = TemporalState.NotPresent,
    val pdfToShowInformation: TemporalState<ScannedPdf> = TemporalState.NotPresent,
    val pdfForOptions: TemporalState<ScannedPdf> = TemporalState.NotPresent,

    // Search & Filter State
    val searchQuery: String = "",
    val isSearchBarVisible: Boolean = false,
    val filterOptions: FilterOptions = FilterOptions.default,

    // Scanning State
    val isScanning: Boolean = false,
    val lastScannedDocument: ScannedDocument? = null,
    val scanUserMessage: String? = null,
) {

    val errorMessage: String?
        get() = (loadState as? LoadState.Error)?.message

    val hasActiveFilters: Boolean
        get() =
            filterOptions.minPageCount != null ||
                    filterOptions.minFileSize != null ||
                    filterOptions.dateRange != null ||
                    filterOptions.sortBy != SortOption.DateDesc

    val isEmptyResult: Boolean
        get() = scannedPdfs.isEmpty() && !isRepositoryEmpty
}
// --- ACTIONS (Inputs from UI) ---
sealed interface HomeUiAction {
    // Scanning
    data object OnScanButtonClicked : HomeUiAction

    data class OnScanResultReceived(val result: Any) : HomeUiAction

    data object OnScanErrorDismissed : HomeUiAction

    // PDF Operations (User Intents)
    data class OpenPdf(val uri: Uri) : HomeUiAction

    data class SavePdf(val pdf: ScannedPdf) : HomeUiAction

    data class SharePdf(val uri: Uri) : HomeUiAction

    data class DeletePdf(val id: String?) : HomeUiAction // null id means cancel/dismiss

    data class UpdatePdfMetadata(val id: String, val title: String, val description: String) :
        HomeUiAction

    // Dialog Triggers
    data class ShowDeleteConfirmation(val id: String) : HomeUiAction

    data class ShowEditDialog(val id: String) : HomeUiAction

    data class ShowPdfInfo(val id: String) : HomeUiAction

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

    data class OpenPdfViewerFailure(val error: Throwable) : HomeUiEffect

    data class SharePdfFailure(val error: Throwable) : HomeUiEffect

    data class ShowPdfInfoDialog(val id: String) : HomeUiEffect
}
