package com.bobbyesp.docucraft.feature.docscanner.presentation.contract

import com.bobbyesp.docucraft.mlkit.domain.model.Document

// TODO: Use these in the UI

data class ScanUiState(
    val isLoading: Boolean = false,
    val document: Document? = null,
    val userMessage: String? = null,
)

sealed class ScanUiAction {
    data object OnScanButtonClicked : ScanUiAction()

    data class OnScanResultReceived(val result: Any) : ScanUiAction()

    data object OnErrorDismissed : ScanUiAction()
}

sealed class ScanUiEffect {
    data class NavigateToPreview(val documentUri: String) : ScanUiEffect()

    data class ShowToast(val message: String) : ScanUiEffect()
}
