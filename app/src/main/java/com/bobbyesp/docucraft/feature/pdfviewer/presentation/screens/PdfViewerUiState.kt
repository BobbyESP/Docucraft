package com.bobbyesp.docucraft.feature.pdfviewer.presentation.screens

import com.bobbyesp.docucraft.core.util.state.ScreenState

data class PdfViewerUiState(
    val state: ScreenState<Unit> = ScreenState.Loading()
)
