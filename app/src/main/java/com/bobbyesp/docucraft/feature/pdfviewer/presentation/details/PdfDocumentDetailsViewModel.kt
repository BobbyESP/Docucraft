/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bobbyesp.docucraft.feature.pdfviewer.domain.details.ObserveViewerDocumentDetailsUseCase
import com.bobbyesp.docucraft.feature.pdfviewer.domain.details.ViewerDocumentDetails
import com.bobbyesp.docucraft.feature.pdfviewer.domain.model.ViewerDocumentRef
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The details destination's state holder, one per entry. */
class PdfDocumentDetailsViewModel(
    ref: ViewerDocumentRef,
    observeDetails: ObserveViewerDocumentDetailsUseCase,
) : ViewModel() {

    val state: StateFlow<PdfDocumentDetailsState> =
        observeDetails(ref)
            .map { details ->
                details?.let(PdfDocumentDetailsState::Ready) ?: PdfDocumentDetailsState.Gone
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = PdfDocumentDetailsState.Loading,
            )
}

/** Waiting and leaving are different answers, as for the viewer itself. */
sealed interface PdfDocumentDetailsState {
    data object Loading : PdfDocumentDetailsState

    /** Deleted while the details were open, from the list beside them on a wide window. */
    data object Gone : PdfDocumentDetailsState

    data class Ready(val details: ViewerDocumentDetails) : PdfDocumentDetailsState
}
