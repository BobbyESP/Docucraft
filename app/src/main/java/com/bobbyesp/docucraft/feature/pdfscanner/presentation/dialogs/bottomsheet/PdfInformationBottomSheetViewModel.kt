package com.bobbyesp.docucraft.feature.pdfscanner.presentation.dialogs.bottomsheet

import androidx.lifecycle.viewModelScope
import com.bobbyesp.docucraft.core.util.state.ResourceState
import com.bobbyesp.docucraft.core.util.viewModel.ViewModelCoroutineBased
import com.bobbyesp.docucraft.feature.pdfscanner.domain.model.ScannedPdf
import com.bobbyesp.docucraft.feature.pdfscanner.domain.repository.usecase.ScannedPdfUseCase
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class PdfInformationBottomSheetViewModel(
    private val scannedPdfUseCase: ScannedPdfUseCase,
) : ViewModelCoroutineBased() {
    override val exceptionHandler: CoroutineExceptionHandler
        get() = CoroutineExceptionHandler { _, exception ->
            // Handle the exception
        }

    private val _uiState: MutableStateFlow<PdfInformationBottomSheetState> = MutableStateFlow(
        PdfInformationBottomSheetState()
    )
    val uiState = _uiState.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), PdfInformationBottomSheetState()
    )

    data class PdfInformationBottomSheetState(
        val actualPdf: ResourceState<ScannedPdf> = ResourceState.Loading(), //We can pass some extra data
    )

    private val _eventFlow = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)
    val eventFlow = _eventFlow.asSharedFlow()

    private fun showPdfInformation(pdfId: String) {
        launchIO {
            try {
                val pdf = scannedPdfUseCase.getScannedPdf(pdfId)

                modifyPdfState(ResourceState.Success(pdf))
            } catch (e: Exception) {
                modifyPdfState(ResourceState.Error(e.message ?: "Unknown error"))
            }
        }
    }

    private fun modifyPdfState(state: ResourceState<ScannedPdf>) {
        _uiState.update {
            it.copy(
                actualPdf = state
            )
        }
    }

    fun onEvent(event: Event) {
        when (event) {
            is Event.ShowPdfInformation -> {
                // Handle the event to show PDF information
                showPdfInformation(event.pdfId)
            }
        }
    }

    interface Event {
        data class ShowPdfInformation(val pdfId: String) : Event
    }

    interface UiEvent {

    }
}