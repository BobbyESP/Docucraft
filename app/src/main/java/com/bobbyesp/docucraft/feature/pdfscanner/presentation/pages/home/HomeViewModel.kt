package com.bobbyesp.docucraft.feature.pdfscanner.presentation.pages.home

import android.app.Activity
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.viewModelScope
import com.bobbyesp.docucraft.core.util.ViewModelCoroutineBased
import com.bobbyesp.docucraft.feature.pdfscanner.domain.model.ScannedPdf
import com.bobbyesp.docucraft.feature.pdfscanner.domain.repository.usecase.ScannedPdfUseCase
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.copyTo
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.dialogs.uri
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.supervisorScope

class HomeViewModel(
    private val scannedPdfUseCase: ScannedPdfUseCase,
    private val gmsDocumentScanner: GmsDocumentScanner,
) : ViewModelCoroutineBased() {
    override val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Coroutine exception: ${throwable.message}", throwable)
        _eventFlow.tryEmit(UiEvent.Error(throwable))
    }

    private val _uiState: MutableStateFlow<HomeViewState> = MutableStateFlow(HomeViewState())
    val uiState = _uiState.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        HomeViewState(),
    )

    data class HomeViewState(
        val scannedPdfs: List<ScannedPdf> = emptyList<ScannedPdf>(),
        val loadingState: LoadingState = LoadingState.Loading,
        val pdfToBeRemoved: ScannedPdf? = null,
        val pdfToBeModified: ScannedPdf? = null,
    )

    private val _eventFlow = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)
    val eventFlow = _eventFlow.asSharedFlow()

    init {
        loadScannedPdfs()
    }

    private fun loadScannedPdfs() {
        launchIO {
            _uiState.update { it.copy(loadingState = LoadingState.Loading) }
            try {
                supervisorScope {
                    scannedPdfUseCase.scannedPdfsListFlow().collect { scannedPdfs ->
                        _uiState.update { it.copy(scannedPdfs = scannedPdfs) }

                        if (_uiState.value.loadingState !is LoadingState.Idle) _uiState.update {
                            it.copy(
                                loadingState = LoadingState.Idle
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to retrieve PDFs: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        loadingState = LoadingState.Error(e, "Failed to load PDFs")
                    )
                }
                emitUiEvent(UiEvent.Error(e))
            }
        }
    }

    suspend fun getPdfById(pdfId: String): ScannedPdf? {
        return scannedPdfUseCase.getScannedPdf(pdfId)
    }

    fun onEvent(event: Event) {
        when (event) {
            is Event.HandlePdfScanningResult -> {
                handleScanningResult(activityResult = event.result)
            }

            is Event.ScanPdf -> {
                if (event.activity == null) {
                    emitUiEvent(
                        UiEvent.ScanResult.Failure(
                            error = IllegalStateException("Activity is null")
                        )
                    )
                    return
                }
                scanPdf(activity = event.activity, listener = event.listener)
            }

            is Event.PdfAction -> {
                when (event) {
                    is Event.PdfAction.Open -> openPdfInViewer(pdfPath = event.pdfPath)
                    is Event.PdfAction.Save -> savePdf(scannedPdf = event.scannedPdf)
                    is Event.PdfAction.Share -> sharePdf(pdfPath = event.pdfPath)
                    is Event.PdfAction.Delete -> {
                        _uiState.update { it.copy(pdfToBeRemoved = null) }
                        event.id?.let {
                            launchIO {
                                val scannedPdf = getPdfById(
                                    event.id
                                ) ?: throw IllegalArgumentException("PDF not found")

                                deletePdf(scannedPdf.path)
                            }
                        }
                    }

                    is Event.PdfAction.ModifyTitleDescription -> {
                        launchIO {
                            val scannedPdf = getPdfById(event.pdfId)
                                ?: throw IllegalArgumentException("PDF not found")
                            val newTitle: String? = if (event.title.isBlank()) null else event.title
                            val newDescription: String? =
                                if (event.description.isBlank()) null else event.description

                            try {
                                scannedPdfUseCase.modifyPdf(scannedPdf.id, newTitle, newDescription)
                                emitUiEvent(UiEvent.ScanResult.Success)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to modify PDF: ${e.message}", e)
                                emitUiEvent(UiEvent.Error(e))
                            } finally {
                                _uiState.update {
                                    it.copy(pdfToBeModified = null)
                                }
                            }
                        }
                    }
                }
            }

            is Event.NotifyUserAction.WarnAboutDeletion -> {
                launchIO {
                    val scannedPdf =
                        getPdfById(event.pdfId) ?: throw IllegalArgumentException("PDF not found")

                    _uiState.update {
                        it.copy(pdfToBeRemoved = scannedPdf)
                    }
                }
            }

            is Event.NotifyUserAction.OpenPdfFieldsDialog -> {
                launchIO {
                    val scannedPdf =
                        getPdfById(event.pdfId) ?: throw IllegalArgumentException("PDF not found")

                    _uiState.update {
                        it.copy(pdfToBeModified = scannedPdf)
                    }
                }
            }

            is Event.NotifyUserAction.DismissPdfTitleDescriptionDialog -> {
                _uiState.update {
                    it.copy(
                        pdfToBeModified = null
                    )
                }
            }
        }
    }


    private fun emitUiEvent(event: UiEvent) {
        _eventFlow.tryEmit(event)
    }

    private fun openPdfInViewer(pdfPath: Uri) {
        try {
            scannedPdfUseCase.openPdfInViewer(pdfPath)
        } catch (e: Exception) {
            emitUiEvent(UiEvent.IssueOpening.PdfViewer(error = e))
        }
    }

    private fun sharePdf(pdfPath: Uri) {
        try {
            scannedPdfUseCase.sharePdf(pdfPath)
        } catch (e: Exception) {
            emitUiEvent(UiEvent.IssueOpening.ShareIntent(error = e))
        }
    }

    private fun deletePdf(pdfPath: Uri) {
        launchIO {
            try {
                scannedPdfUseCase.deleteScannedPdf(pdfPath)
                emitUiEvent(UiEvent.DeleteResult.Success)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete PDF: ${e.message}", e)
                emitUiEvent(UiEvent.DeleteResult.Failure(error = e))
            }
        }
    }

    private fun scanPdf(
        activity: Activity,
        listener: ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult>,
    ) {
        gmsDocumentScanner.getStartScanIntent(activity).addOnSuccessListener { intentSender ->
            listener.launch(IntentSenderRequest.Builder(intentSender).build())
        }.addOnFailureListener { exception ->
            Log.e(TAG, "Failed to start scan: ${exception.message}", exception)
            emitUiEvent(UiEvent.ScanResult.Failure(error = exception))
        }
    }

    private fun savePdf(scannedPdf: ScannedPdf) {
        launchIO { copyPdfToDirectory(scannedPdf) }
    }

    private suspend fun copyPdfToDirectory(scannedPdf: ScannedPdf) {
        try {
            val androidDocumentsDirectory = PlatformFile(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            )

            val dir = PlatformFile(androidDocumentsDirectory, "Docucraft")

            val file = FileKit.openFileSaver(
                suggestedName = scannedPdf.title ?: scannedPdf.filename,
                extension = "pdf",
                directory = dir,
            ) ?: run {
                emitUiEvent(UiEvent.SavingResult.Cancelled)
                return
            }

            val internalPdf = PlatformFile(scannedPdf.path)

            internalPdf.copyTo(file)

            emitUiEvent(UiEvent.SavingResult.Success(file.uri))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save PDF: ${e.message}", e)
            emitUiEvent(UiEvent.SavingResult.Failure(error = e))
        }
    }

    private fun handleScanningResult(activityResult: ActivityResult) {
        when (activityResult.resultCode) {
            Activity.RESULT_OK -> {
                val data = activityResult.data
                if (data == null) {
                    emitUiEvent(
                        UiEvent.ScanResult.Failure(
                            error = IllegalStateException("Scan result intent data is null")
                        )
                    )
                    return
                }

                val scannerResult = GmsDocumentScanningResult.fromActivityResultIntent(data)
                if (scannerResult == null) {
                    emitUiEvent(
                        UiEvent.ScanResult.Failure(
                            error = IllegalStateException("Scanner result could not be parsed")
                        )
                    )
                    return
                }

                val scannedPdf = scannerResult.pdf
                if (scannedPdf == null) {
                    emitUiEvent(
                        UiEvent.ScanResult.Failure(
                            error = IllegalStateException("Scanned PDF is null")
                        )
                    )
                    return
                }

                launchIO {
                    try {
                        scannedPdfUseCase.saveScannedPdf(scannedPdf)
                        emitUiEvent(UiEvent.ScanResult.Success)
                    } catch (th: Throwable) {
                        Log.e(TAG, "Failed to save the scanned PDF: ${th.message}", th)
                        emitUiEvent(UiEvent.ScanResult.Failure(error = th))
                    }
                }
            }

            Activity.RESULT_CANCELED -> {
                emitUiEvent(UiEvent.ScanResult.Cancelled)
            }

            else -> {
                emitUiEvent(
                    UiEvent.ScanResult.Failure(
                        error = IllegalStateException(
                            "Unknown result code: ${activityResult.resultCode}"
                        )
                    )
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up any resources that need to be released
    }

    interface Event {
        data class ScanPdf(
            val activity: Activity?,
            val listener: ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult>,
        ) : Event

        data object ReloadPdfs : Event

        data class HandlePdfScanningResult(val result: ActivityResult) : Event

        sealed class PdfAction : Event {
            data class Open(val pdfPath: Uri) : PdfAction()

            data class Save(val scannedPdf: ScannedPdf) : PdfAction()

            data class Share(val pdfPath: Uri) : PdfAction()

            data class Delete(val id: String?) : PdfAction()

            data class ModifyTitleDescription(
                val title: String,
                val description: String,
                val pdfId: String,
            ) : PdfAction()
        }

        sealed class NotifyUserAction : Event {
            data class WarnAboutDeletion(val pdfId: String) : NotifyUserAction()

            data class OpenPdfFieldsDialog(val pdfId: String) : NotifyUserAction()

            data object DismissPdfTitleDescriptionDialog : NotifyUserAction()
        }
    }

    interface UiEvent {
        sealed class ScanResult : UiEvent {
            data object Success : ScanResult()

            data class Failure(val error: Throwable) : ScanResult()

            data object Cancelled : ScanResult()
        }

        sealed class IssueOpening : UiEvent {
            data class PdfViewer(val error: Throwable) : IssueOpening()

            data class ShareIntent(val error: Throwable) : IssueOpening()
        }

        sealed class SavingResult : UiEvent {
            data class Success(val uri: Uri) : SavingResult()

            data class Failure(val error: Throwable) : SavingResult()

            data object Cancelled : SavingResult()
        }

        sealed class DeleteResult : UiEvent {
            data object Success : DeleteResult()

            data class Failure(val error: Throwable) : DeleteResult()
        }

        data class Error(val error: Throwable) : UiEvent
    }

    sealed class LoadingState {
        data object Loading : LoadingState()

        data object Idle : LoadingState()

        data class Error(val error: Throwable, val message: String? = null) : LoadingState()
    }

    companion object {
        private const val TAG = "HomeViewModel"
    }
}