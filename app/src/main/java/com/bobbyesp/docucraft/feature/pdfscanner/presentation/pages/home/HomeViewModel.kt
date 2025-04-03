package com.bobbyesp.docucraft.feature.pdfscanner.presentation.pages.home

import android.app.Activity
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.viewModelScope
import com.bobbyesp.docucraft.core.util.state.TemporalState
import com.bobbyesp.docucraft.core.util.state.TemporalState.NotPresent
import com.bobbyesp.docucraft.core.util.state.TemporalState.Present
import com.bobbyesp.docucraft.core.util.viewModel.ViewModelCoroutineBased
import com.bobbyesp.docucraft.feature.pdfscanner.domain.FilterOptions
import com.bobbyesp.docucraft.feature.pdfscanner.domain.SortOption
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class HomeViewModel(
    private val scannedPdfUseCase: ScannedPdfUseCase,
    private val gmsDocumentScanner: GmsDocumentScanner,
) : ViewModelCoroutineBased() {
    override val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Coroutine exception: ${throwable.message}", throwable)
        _eventFlow.tryEmit(UiEvent.Error(throwable))
    }

    private val _uiState: MutableStateFlow<HomeViewState> = MutableStateFlow(HomeViewState())
    val uiState =
        _uiState.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeViewState())

    data class HomeViewState(
        val scannedPdfs: List<ScannedPdf> = emptyList(),
        val loadingState: LoadingState = LoadingState.Loading,
        val pdfToBeRemoved: TemporalState<ScannedPdf> = NotPresent,
        val pdfToBeModified: TemporalState<ScannedPdf> = NotPresent,
        val pdfToShowInformation: TemporalState<ScannedPdf> = NotPresent,
        val searchState: SearchViewState = SearchViewState(),
        val filterOptions: FilterOptions = FilterOptions(),
        val filteredPdfs: List<ScannedPdf> = emptyList(),
    )

    data class SearchViewState(
        val searchQuery: String = "",
        val showingSearchBar: Boolean = false,
    )

    private val _eventFlow = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)
    val eventFlow = _eventFlow.asSharedFlow()

    init {
        launchIO { loadScannedPdfs() }
    }

    private suspend fun loadScannedPdfs() {
        _uiState.update { it.copy(loadingState = LoadingState.Loading) }
        scannedPdfUseCase.scannedPdfsListFlow().catch { e ->
            Log.e(TAG, "Failed to retrieve PDFs: ${e.message}", e)
            _uiState.update {
                it.copy(loadingState = LoadingState.Error(e, "Failed to load PDFs"))
            }
            emitUiEvent(UiEvent.Error(e))
        }.collect { scannedPdfs ->
            _uiState.update { it.copy(scannedPdfs = scannedPdfs) }
            if (_uiState.value.hasActiveFilters) applySearchAndFilters()
            if (_uiState.value.loadingState !is LoadingState.Idle) _uiState.update {
                it.copy(
                    loadingState = LoadingState.Idle
                )
            }
        }
    }

    private val HomeViewState.hasActiveFilters: Boolean
        get() = filterOptions.minPageCount != null || filterOptions.minFileSize != null || filterOptions.dateRange != null || filterOptions.sortBy != SortOption.DateDesc

    private fun applySearchAndFilters() {
        launchIO {
            val currentState = _uiState.value
            val query = currentState.searchState.searchQuery
            val filterOptions = currentState.filterOptions

            var result = currentState.scannedPdfs

            if (query.isNotBlank()) {
                try {
                    // Use the existing DAO methods for search
                    val searchResults = scannedPdfUseCase.searchPdfs(query)
                    result = result.filter { pdf -> searchResults.any { it.id == pdf.id } }
                } catch (e: Exception) {
                    Log.e(TAG, "Search failed: ${e.message}", e)
                    // Fallback to in-memory filtering
                    result = result.filter { pdf ->
                        pdf.title?.contains(
                            query, ignoreCase = true
                        ) == true || pdf.filename.contains(
                            query, ignoreCase = true
                        ) || pdf.description?.contains(query, ignoreCase = true) == true
                    }
                }
            }

            filterOptions.minPageCount?.let { minPages ->
                result = result.filter { it.pageCount >= minPages }
            }

            filterOptions.minFileSize?.let { minSize ->
                result = result.filter { it.fileSize >= minSize }
            }

            filterOptions.dateRange?.let { (start, end) ->
                result = result.filter { it.createdTimestamp in start..end }
            }

            result = when (filterOptions.sortBy.criteria) {
                SortOption.Criteria.DATE -> {
                    if (filterOptions.sortBy.order == SortOption.Order.DESC) result.sortedByDescending { it.createdTimestamp }
                    else result.sortedBy { it.createdTimestamp }
                }

                SortOption.Criteria.NAME -> {
                    if (filterOptions.sortBy.order == SortOption.Order.DESC) result.sortedByDescending {
                        it.title ?: it.filename
                    }
                    else result.sortedBy { it.title ?: it.filename }
                }

                SortOption.Criteria.SIZE -> {
                    if (filterOptions.sortBy.order == SortOption.Order.DESC) result.sortedByDescending { it.fileSize }
                    else result.sortedBy { it.fileSize }
                }
            }

            _uiState.update { it.copy(filteredPdfs = result) }
        }
    }

    suspend fun getPdfById(pdfId: String): ScannedPdf {
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
                    is Event.PdfAction.Save -> launchIO { copyPdfToDirectory(event.scannedPdf) }
                    is Event.PdfAction.Share -> sharePdf(pdfPath = event.pdfPath)
                    is Event.PdfAction.Delete -> {
                        _uiState.update { it.copy(pdfToBeRemoved = NotPresent) }
                        event.id?.let {
                            launchIO {
                                val scannedPdf = getPdfById(event.id)

                                deletePdf(scannedPdf.path)
                            }
                        }
                    }

                    is Event.PdfAction.ModifyTitleDescription -> {
                        launchIO {
                            val scannedPdf = getPdfById(event.pdfId)
                            val newTitle: String? = event.title.ifBlank { null }
                            val newDescription: String? = event.description.ifBlank { null }

                            try {
                                scannedPdfUseCase.modifyPdf(scannedPdf.id, newTitle, newDescription)
                                emitUiEvent(UiEvent.ScanResult.Success)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to modify PDF: ${e.message}", e)
                                emitUiEvent(UiEvent.Error(e))
                            } finally {
                                _uiState.update { it.copy(pdfToBeModified = NotPresent) }
                            }
                        }
                    }
                }
            }

            is Event.NotifyUserAction -> {
                when (event) {
                    is Event.NotifyUserAction.DismissPdfTitleDescriptionDialog -> {
                        _uiState.update { it.copy(pdfToBeModified = NotPresent) }
                    }

                    is Event.NotifyUserAction.WarnAboutDeletion -> {
                        launchIO {
                            val scannedPdf = getPdfById(event.pdfId)

                            _uiState.update {
                                it.copy(
                                    pdfToBeRemoved = Present(
                                        scannedPdf
                                    )
                                )
                            }
                        }
                    }

                    is Event.NotifyUserAction.OpenPdfFieldsDialog -> {
                        launchIO {
                            val scannedPdf = getPdfById(event.pdfId)

                            _uiState.update {
                                it.copy(
                                    pdfToBeModified = Present(
                                        scannedPdf
                                    )
                                )
                            }
                        }
                    }

                    is Event.NotifyUserAction.ShowPdfInformation -> {
                        emitUiEvent(
                            UiEvent.PdfInformation.Show(event.id)
                        )
                    }
                }
            }

            is Event.SearchFilterEvent -> {
                when (event) {
                    is Event.SearchFilterEvent.ApplyFilter -> {
                        _uiState.update { it.copy(filterOptions = event.filterOptions) }
                    }

                    Event.SearchFilterEvent.ClearFilters -> {
                        _uiState.update { it.copy(filterOptions = FilterOptions()) }
                    }

                    Event.SearchFilterEvent.ClearSearch -> {
                        _uiState.update { it.copy(searchState = it.searchState.copy(searchQuery = "")) }
                    }

                    is Event.SearchFilterEvent.UpdateSearchQuery -> {
                        _uiState.update {
                            it.copy(
                                searchState = it.searchState.copy(searchQuery = event.query)
                            )
                        }
                    }

                    is Event.SearchFilterEvent.ApplySort -> {
                        _uiState.update {
                            it.copy(
                                filterOptions = it.filterOptions.copy(sortBy = event.sortOption)
                            )
                        }
                    }
                }
                applySearchAndFilters()
            }

            is Event.SearchUIEvent -> {
                when (event) {
                    is Event.SearchUIEvent.ShowSearchBar -> {
                        _uiState.update { it.copy(searchState = it.searchState.copy(showingSearchBar = event.show)) }
                    }
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

    private suspend fun deletePdf(pdfPath: Uri) {
        try {
            scannedPdfUseCase.deleteScannedPdf(pdfPath)
            emitUiEvent(UiEvent.DeleteResult.Success)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete PDF: ${e.message}", e)
            emitUiEvent(UiEvent.DeleteResult.Failure(error = e))
        }
    }

    private fun scanPdf(
        activity: Activity,
        listener: ActivityResultLauncher<IntentSenderRequest>,
    ) {
        gmsDocumentScanner.getStartScanIntent(activity).addOnSuccessListener { intentSender ->
            listener.launch(IntentSenderRequest.Builder(intentSender).build())
        }.addOnFailureListener { exception ->
            Log.e(TAG, "Failed to start scan: ${exception.message}", exception)
            emitUiEvent(UiEvent.ScanResult.Failure(error = exception))
        }
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
            val listener: ActivityResultLauncher<IntentSenderRequest>,
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

            data class ShowPdfInformation(val id: String) : NotifyUserAction()
        }

        sealed class SearchFilterEvent : Event {
            data class UpdateSearchQuery(val query: String) : SearchFilterEvent()

            data object ClearSearch : SearchFilterEvent()

            data class ApplySort(val sortOption: SortOption) : SearchFilterEvent()

            data class ApplyFilter(val filterOptions: FilterOptions) : SearchFilterEvent()

            data object ClearFilters : SearchFilterEvent()
        }

        sealed class SearchUIEvent : Event {
            data class ShowSearchBar(val show: Boolean) : SearchUIEvent()
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

        sealed class PdfInformation : UiEvent {
            data class Show(val id: String) : PdfInformation()
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
