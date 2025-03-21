package com.bobbyesp.docucraft.feature.pdfscanner.presentation.pages.home

import android.app.Activity
import android.net.Uri
import android.util.Log
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bobbyesp.docucraft.feature.pdfscanner.domain.model.ScannedPdf
import com.bobbyesp.docucraft.feature.pdfscanner.domain.repository.ScannedPdfRepository
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.pages.home.HomeViewModel.Event.HandlePdfScanningResult
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val scannedPdfRepository: ScannedPdfRepository,
    private val gmsDocumentScanner: GmsDocumentScanner,
) : ViewModel() {
    private val mutableScannedPdfsListFlow = MutableStateFlow<List<ScannedPdf>>(emptyList())
    val scannedPdfsListFlow = mutableScannedPdfsListFlow.onStart {
        //Do things before it start to collect
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    private val _eventFlow = MutableSharedFlow<UiEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    init {
        viewModelScope.launch {
            try {
                scannedPdfRepository.getAllScannedPdfsFlow().collect { mappedPdfs ->
                    mutableScannedPdfsListFlow.update {
                        mappedPdfs
                    }
                }
            } catch (e: Exception) {
                Log.e(
                    "HomeViewModel",
                    "Something unexpected happened while retrieving the PDFs: \n" + e.message
                )
            }
        }
    }

    private fun emitUiEvent(event: UiEvent) {
        viewModelScope.launch {
            _eventFlow.emit(event)
        }
    }

    fun onEvent(event: Event) {
        when (event) {
            is HandlePdfScanningResult -> {
                handleScanningResult(activityResult = event.result)
            }
            is Event.ScanPdf -> {

                if(event.activity == null) {
                    emitUiEvent(UiEvent.ScanResult.Failure(error = IllegalStateException("Activity is null")))
                    return
                }

                scanPdf(activity = event.activity, listener = event.listener)
            }

            is Event.OpenPdfInViewer -> {
                openPdfInViewer(pdfPath = event.pdfPath)
            }

            is Event.SharePdf -> {
                sharePdf(pdfPath = event.pdfPath)
            }
        }
    }

    fun scanPdf(
        activity: Activity,
        listener: ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult>
    ) {
        gmsDocumentScanner.getStartScanIntent(activity).addOnSuccessListener {
            listener.launch(
                IntentSenderRequest.Builder(it).build()
            )
        }
    }

    fun openPdfInViewer(pdfPath: Uri) {
        try {
            scannedPdfRepository.openPdfInViewer(pdfPath)
        } catch (e: Exception) {
            emitUiEvent(UiEvent.IssueOpening.PdfViewer(error = e))
        }
    }

    fun sharePdf(pdfPath: Uri) {
        try {
            scannedPdfRepository.sharePdf(pdfPath)
        } catch (e: Exception) {
            emitUiEvent(UiEvent.IssueOpening.ShareIntent(error = e))
        }
    }

    fun handleScanningResult(activityResult: ActivityResult) {
        if (activityResult.resultCode == Activity.RESULT_OK) {
            val scannerResult =
                GmsDocumentScanningResult.fromActivityResultIntent(activityResult.data)
            val scannedPdf = scannerResult?.pdf

            if (scannedPdf == null) {
                emitUiEvent(UiEvent.ScanResult.Failure(error = IllegalStateException("Scanned PDF is null")))
                return
            } else {
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching {
                        scannedPdfRepository.savePdf(scannedPdf)
                    }.onSuccess {
                        emitUiEvent(UiEvent.ScanResult.Success)
                    }.onFailure { th ->
                        Log.e(
                            "HomeViewModel",
                            "Failed to save the scanned PDF: \n" + th.message
                        )
                        emitUiEvent(
                            UiEvent.ScanResult.Failure(
                                error = th
                            )
                        )
                    }
                }
            }
        }
    }

    interface Event {
        data class ScanPdf(
            val activity: Activity?,
            val listener: ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult>
        ) : Event

        data class HandlePdfScanningResult(val result: ActivityResult) : Event

        data class OpenPdfInViewer(val pdfPath: Uri) : Event
        data class SharePdf(val pdfPath: Uri) : Event
    }

    interface UiEvent {
        sealed class ScanResult : UiEvent {
            data object Success : ScanResult()
            data class Failure(val error: Throwable) : ScanResult()
        }

        sealed class IssueOpening : UiEvent {
            data class PdfViewer(val error: Throwable) : IssueOpening()
            data class ShareIntent(val error: Throwable) : IssueOpening()
        }
    }
}