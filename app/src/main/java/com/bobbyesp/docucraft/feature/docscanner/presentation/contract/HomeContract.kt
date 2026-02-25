package com.bobbyesp.docucraft.feature.docscanner.presentation.contract

import android.net.Uri
import com.bobbyesp.docucraft.core.presentation.common.DialogBackStack
import com.bobbyesp.docucraft.core.presentation.common.Route
import com.bobbyesp.docucraft.feature.docscanner.domain.FilterOptions
import com.bobbyesp.docucraft.feature.docscanner.domain.SortOption
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.mlkit.domain.model.Document

sealed interface HomeStatus {
    data object Idle : HomeStatus
    data object Loading : HomeStatus
    data class Error(val message: String) : HomeStatus
}

sealed interface HomeDialog {
    data class Delete(val doc: ScannedDocument) : HomeDialog
    data class Edit(val doc: ScannedDocument) : HomeDialog
    data class Actions(val doc: ScannedDocument) : HomeDialog
}

data class HomeUiState(
    val status: HomeStatus = HomeStatus.Loading,
    val visibleDocuments: List<ScannedDocument> = emptyList(),
    val hasDocuments: Boolean = false,
    val dialogs: DialogBackStack<HomeDialog> = DialogBackStack(),
    val searchQuery: String = "",
    val isSearchBarVisible: Boolean = false,
    val filterOptions: FilterOptions = FilterOptions.default,
    val isScanning: Boolean = false,
    val mostRecentScan: Document? = null,
) {
    val activeDialog: HomeDialog? = dialogs.active
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

    data class ScanResultAction(val result: Any) : HomeUiAction

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

    data class ShowActionsSheet(val id: String) : HomeUiAction

    data object DismissActionsSheet : HomeUiAction

    data object DismissDialogs : HomeUiAction

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
