package com.bobbyesp.docucraft.feature.pdfviewer.presentation.screens

import com.bhuvaneshw.pdf.compose.PdfState
import com.bobbyesp.docucraft.core.util.viewModel.CoroutineBasedViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PdfViewerViewModel : CoroutineBasedViewModel() {

    private val _uiState = MutableStateFlow(PdfViewerUiState())
    val uiState: StateFlow<PdfViewerUiState> = _uiState.asStateFlow()

    private val _pdfState: MutableStateFlow<PdfState?> = MutableStateFlow(null)
    val pdfState: StateFlow<PdfState?> = _pdfState.asStateFlow()


}