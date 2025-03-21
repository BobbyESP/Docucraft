package com.bobbyesp.docucraft.feature.pdfscanner.presentation.pages.home

import android.app.Activity
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bobbyesp.docucraft.core.util.state.ResourceState
import com.bobbyesp.docucraft.feature.pdfscanner.domain.model.ScannedPdf
import com.bobbyesp.docucraft.feature.pdfscanner.domain.repository.ScannedPdfRepository
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.pages.home.HomeViewModel.Event.HandlePdfScanningResult
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.copyTo
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.dialogs.uri
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
    private val mutableScannedPdfsListFlow = MutableStateFlow<ResourceState<List<ScannedPdf>>>(
        ResourceState.Loading()
    )
    val scannedPdfsListFlow = mutableScannedPdfsListFlow.onStart {
        //Do things before it start to collect
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), ResourceState.Loading()
    )

    private val _eventFlow = MutableSharedFlow<UiEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    init {
        viewModelScope.launch {
            try {
                scannedPdfRepository.getAllScannedPdfsFlow().collect { mappedPdfs ->
                    mutableScannedPdfsListFlow.update {
                        ResourceState.Success(mappedPdfs)
                    }
                }
            } catch (e: Exception) {
                Log.e(
                    "HomeViewModel",
                    "Something unexpected happened while retrieving the PDFs: \n" + e.message
                )
                mutableScannedPdfsListFlow.update {
                    ResourceState.Error(errorMessage = e.message ?: "An unexpected error occurred")
                }
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

                if (event.activity == null) {
                    emitUiEvent(UiEvent.ScanResult.Failure(error = IllegalStateException("Activity is null")))
                    return
                }

                scanPdf(activity = event.activity, listener = event.listener)
            }

            is Event.PdfAction -> {
                when (event) {
                    is Event.PdfAction.Open -> {
                        openPdfInViewer(pdfPath = event.pdfPath)
                    }

                    is Event.PdfAction.Save -> {
                        viewModelScope.launch {
                            copyPdfToDirectory(scannedPdf = event.scannedPdf)
                        }
                    }

                    is Event.PdfAction.Share -> {
                        sharePdf(pdfPath = event.pdfPath)
                    }

                    is Event.PdfAction.Delete -> {
                        // Do nothing
                    }
                }
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

    suspend fun copyPdfToDirectory(scannedPdf: ScannedPdf) {
        try {
            val androidDocumentsDirectory = PlatformFile(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            )

            val dir = PlatformFile(
                androidDocumentsDirectory, "Docucraft"
            )

            val file = FileKit.openFileSaver(
                suggestedName = scannedPdf.title ?: scannedPdf.filename,
                extension = "pdf",
                directory = dir,
            )

            if (file == null) {
                emitUiEvent(UiEvent.SavingResult.Failure(error = IllegalStateException("File selection cancelled or failed")))
                return
            }

            val internalPdf = PlatformFile(scannedPdf.path)
            internalPdf.copyTo(file)

            // Emit success event with the saved file URI
            emitUiEvent(UiEvent.SavingResult.Success(file.uri))

        } catch (e: Exception) {
            Log.e("HomeViewModel", "Failed to save PDF: ${e.message}", e)
            emitUiEvent(UiEvent.SavingResult.Failure(error = e))
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
                            "HomeViewModel", "Failed to save the scanned PDF: \n" + th.message
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

        sealed class PdfAction : Event {
            data class Open(val pdfPath: Uri) : PdfAction()
            data class Save(val scannedPdf: ScannedPdf) : PdfAction()
            data class Share(val pdfPath: Uri) : PdfAction()
            data class Delete(val pdfPath: Uri) : PdfAction()
        }
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

        sealed class SavingResult : UiEvent {
            data class Success(val uri: Uri) : SavingResult()
            data class Failure(val error: Throwable) : SavingResult()
        }
    }
}