package com.bobbyesp.docucraft.feature.pdfscanner.presentation.contract

import com.bobbyesp.docucraft.core.domain.model.ScannedDocument

data class ScanUiState(
    val isLoading: Boolean = false,
    val scannedDocument: ScannedDocument? = null,
    val userMessage: String? = null
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
