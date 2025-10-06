package com.bobbyesp.docucraft.feature.pdfscanner.presentation.pages.home.viewmodel

import android.app.Activity
import android.net.Uri
import android.os.Environment
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.core.net.toUri
import com.bobbyesp.docucraft.core.util.state.TemporalState
import com.bobbyesp.docucraft.core.util.viewModel.CoroutineBasedViewModel
import com.bobbyesp.docucraft.feature.pdfscanner.domain.FilterOptions
import com.bobbyesp.docucraft.feature.pdfscanner.domain.SortOption
import com.bobbyesp.docucraft.feature.pdfscanner.domain.model.ScannedPdf
import com.bobbyesp.docucraft.feature.pdfscanner.domain.usecase.*
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.copyTo
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for the Home screen.
 * Now uses individual use cases instead of a monolithic "UseCase" interface.
 * Each use case has a single responsibility, following SOLID principles.
 */
class HomeViewModel(
    private val getAllScannedPdfsUseCase: GetAllScannedPdfsUseCase,
    private val searchPdfsUseCase: SearchPdfsUseCase,
    private val getScannedPdfByIdUseCase: GetScannedPdfByIdUseCase,
    private val saveScannedPdfUseCase: SaveScannedPdfUseCase,
    private val deleteScannedPdfUseCase: DeleteScannedPdfUseCase,
    private val updatePdfMetadataUseCase: UpdatePdfMetadataUseCase,
    private val openPdfInViewerUseCase: OpenPdfInViewerUseCase,
    private val sharePdfUseCase: SharePdfUseCase,
    private val gmsDocumentScanner: GmsDocumentScanner,
) : CoroutineBasedViewModel() {

    // State management using new helpers
    private val _uiState = MutableStateFlow(HomeViewState())
    val uiState: StateFlow<HomeViewState> = _uiState.asStateFlow()

    // Events using new helper
    private val eventFlowPair = createEventFlow<UiEvent>()
    private val _eventFlow: MutableSharedFlow<UiEvent> = eventFlowPair.first
    val eventFlow: SharedFlow<UiEvent> = eventFlowPair.second

    data class HomeViewState(
        val scannedPdfs: List<ScannedPdf> = emptyList(),
        val loadingState: LoadingState = LoadingState.Loading,
        val pdfToBeRemoved: TemporalState<ScannedPdf> = TemporalState.NotPresent,
        val pdfToBeModified: TemporalState<ScannedPdf> = TemporalState.NotPresent,
        val pdfToShowInformation: TemporalState<ScannedPdf> = TemporalState.NotPresent,
        val searchState: SearchViewState = SearchViewState(),
        val filterOptions: FilterOptions = FilterOptions.default,
        val filteredPdfs: List<ScannedPdf> = emptyList(),
    )

    data class SearchViewState(val searchQuery: String = "", val showingSearchBar: Boolean = false)

    init {
        // Load PDFs on initialization with safe collection
        loadScannedPdfs()
    }

    override fun onCoroutineException(throwable: Throwable) {
        _eventFlow.emitEvent(UiEvent.Error(throwable))
    }

    private fun loadScannedPdfs() {
        launchIO {
            getAllScannedPdfsUseCase()
                .collectSafely(
                    onLoading = {
                        _uiState.updateValue { it.copy(loadingState = LoadingState.Loading) }
                    },
                    onEach = { scannedPdfs ->
                        _uiState.updateValue { state ->
                            state.copy(scannedPdfs = scannedPdfs)
                        }

                        if (_uiState.value.hasActiveFilters) {
                            applySearchAndFilters()
                        }

                        if (_uiState.value.loadingState !is LoadingState.Idle) {
                            _uiState.updateValue { it.copy(loadingState = LoadingState.Idle) }
                        }
                    },
                    onError = { error ->
                        logError("Failed to retrieve PDFs: ${error.message}", error)
                        _uiState.updateValue {
                            it.copy(loadingState = LoadingState.Error(error, "Failed to load PDFs"))
                        }
                        _eventFlow.emitEvent(UiEvent.Error(error))
                    }
                )
        }
    }

    private val HomeViewState.hasActiveFilters: Boolean
        get() =
            filterOptions.minPageCount != null ||
                filterOptions.minFileSize != null ||
                filterOptions.dateRange != null ||
                filterOptions.sortBy != SortOption.DateDesc

    private fun applySearchAndFilters() {
        launchIO {
            val currentState = _uiState.value
            val query = currentState.searchState.searchQuery
            val filterOptions = currentState.filterOptions

            var result = currentState.scannedPdfs

            if (query.isNotBlank()) {
                try {
                    val searchResults = searchPdfsUseCase(query)
                    result = result.filter { pdf -> searchResults.any { it.id == pdf.id } }
                } catch (e: Exception) {
                    logError("Search failed: ${e.message}", e)
                    // Fallback to in-memory filtering
                    result =
                        result.filter { pdf ->
                            pdf.title?.contains(query, ignoreCase = true) == true ||
                                pdf.filename.contains(query, ignoreCase = true) ||
                                pdf.description?.contains(query, ignoreCase = true) == true
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

            result =
                when (filterOptions.sortBy.criteria) {
                    SortOption.Criteria.DATE -> {
                        if (filterOptions.sortBy.order == SortOption.Order.DESC)
                            result.sortedByDescending { it.createdTimestamp }
                        else result.sortedBy { it.createdTimestamp }
                    }

                    SortOption.Criteria.NAME -> {
                        if (filterOptions.sortBy.order == SortOption.Order.DESC)
                            result.sortedByDescending { it.title ?: it.filename }
                        else result.sortedBy { it.title ?: it.filename }
                    }

                    SortOption.Criteria.SIZE -> {
                        if (filterOptions.sortBy.order == SortOption.Order.DESC)
                            result.sortedByDescending { it.fileSize }
                        else result.sortedBy { it.fileSize }
                    }
                }

            _uiState.updateValue { it.copy(filteredPdfs = result) }
        }
    }

    suspend fun getPdfById(pdfId: String): ScannedPdf {
        return getScannedPdfByIdUseCase(pdfId)
    }

    fun onEvent(event: Event) {
        when (event) {
            is Event.HandlePdfScanningResult -> {
                handleScanningResult(activityResult = event.result)
            }

            is Event.ScanPdf -> {
                if (event.activity == null) {
                    _eventFlow.emitEvent(
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
                    is Event.PdfAction.Save -> copyPdfToDirectory(event.scannedPdf)
                    is Event.PdfAction.Share -> sharePdf(pdfPath = event.pdfPath)
                    is Event.PdfAction.Delete -> {
                        _uiState.updateValue { it.copy(pdfToBeRemoved = TemporalState.NotPresent) }
                        event.id?.let {
                            launchIO {
                                val scannedPdf = getPdfById(event.id)
                                deletePdf(scannedPdf.path)
                            }
                        }
                    }

                    is Event.PdfAction.ModifyTitleDescription -> {
                        modifyPdfMetadata(
                            pdfId = event.pdfId,
                            title = event.title,
                            description = event.description
                        )
                    }
                }
            }

            is Event.NotifyUserAction -> {
                when (event) {
                    is Event.NotifyUserAction.DismissPdfTitleDescriptionDialog -> {
                        _uiState.updateValue { it.copy(pdfToBeModified = TemporalState.NotPresent) }
                    }

                    is Event.NotifyUserAction.WarnAboutDeletion -> {
                        launchIO {
                            val scannedPdf = getPdfById(event.pdfId)
                            _uiState.updateValue {
                                it.copy(pdfToBeRemoved = TemporalState.Present(scannedPdf))
                            }
                        }
                    }

                    is Event.NotifyUserAction.OpenPdfFieldsDialog -> {
                        launchIO {
                            val scannedPdf = getPdfById(event.pdfId)
                            _uiState.updateValue {
                                it.copy(pdfToBeModified = TemporalState.Present(scannedPdf))
                            }
                        }
                    }

                    is Event.NotifyUserAction.ShowPdfInformation -> {
                        _eventFlow.emitEvent(UiEvent.PdfInformation.Show(event.id))
                    }
                }
            }

            is Event.SearchFilterEvent -> {
                when (event) {
                    is Event.SearchFilterEvent.ApplyFilter -> {
                        _uiState.updateValue { it.copy(filterOptions = event.filterOptions) }
                    }

                    Event.SearchFilterEvent.ClearFilters -> {
                        _uiState.updateValue { it.copy(filterOptions = FilterOptions.default) }
                    }

                    Event.SearchFilterEvent.ClearSearch -> {
                        _uiState.updateValue {
                            it.copy(searchState = it.searchState.copy(searchQuery = ""))
                        }
                    }

                    is Event.SearchFilterEvent.UpdateSearchQuery -> {
                        _uiState.updateValue {
                            it.copy(searchState = it.searchState.copy(searchQuery = event.query))
                        }
                    }

                    is Event.SearchFilterEvent.ApplySort -> {
                        _uiState.updateValue {
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
                        _uiState.updateValue {
                            it.copy(
                                searchState = it.searchState.copy(showingSearchBar = event.show)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun modifyPdfMetadata(pdfId: String, title: String, description: String) {
        executeAsync(
            onSuccess = {
                logInfo("PDF metadata updated successfully")
                _eventFlow.emitEvent(UiEvent.ScanResult.Success)
            },
            onError = { error ->
                logError("Failed to modify PDF: ${error.message}", error)
                _eventFlow.emitEvent(UiEvent.Error(error))
            }
        ) {
            val scannedPdf = getPdfById(pdfId)
            val newTitle: String? = title.ifBlank { null }
            val newDescription: String? = description.ifBlank { null }

            updatePdfMetadataUseCase(scannedPdf.id, newTitle, newDescription)

            _uiState.updateValue {
                it.copy(pdfToBeModified = TemporalState.NotPresent)
            }
        }
    }

    private fun openPdfInViewer(pdfPath: Uri) {
        try {
            openPdfInViewerUseCase(pdfPath)
        } catch (e: Exception) {
            _eventFlow.emitEvent(UiEvent.IssueOpening.PdfViewer(error = e))
        }
    }

    private fun sharePdf(pdfPath: Uri) {
        try {
            sharePdfUseCase(pdfPath)
        } catch (e: Exception) {
            _eventFlow.emitEvent(UiEvent.IssueOpening.ShareIntent(error = e))
        }
    }

    private suspend fun deletePdf(pdfPath: Uri) {
        try {
            deleteScannedPdfUseCase(pdfPath)
            _eventFlow.emitEvent(UiEvent.DeleteResult.Success)
        } catch (e: Exception) {
            logError("Failed to delete PDF: ${e.message}", e)
            _eventFlow.emitEvent(UiEvent.DeleteResult.Failure(error = e))
        }
    }

    private fun scanPdf(activity: Activity, listener: ActivityResultLauncher<IntentSenderRequest>) {
        gmsDocumentScanner
            .getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                listener.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { exception ->
                logError("Failed to start scan: ${exception.message}", exception)
                _eventFlow.emitEvent(UiEvent.ScanResult.Failure(error = exception))
            }
    }

    private fun copyPdfToDirectory(scannedPdf: ScannedPdf) {
        executeAsync(
            onSuccess = { uri: Uri ->
                logInfo("PDF saved successfully to: $uri")
                _eventFlow.emitEvent(UiEvent.SavingResult.Success(uri))
            },
            onError = { error ->
                logError("Failed to save PDF: ${error.message}", error)
                _eventFlow.emitEvent(UiEvent.SavingResult.Failure(error = error))
            }
        ) {
            val androidDocumentsDirectory =
                PlatformFile(
                    Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOCUMENTS
                    )
                )

            val dir = PlatformFile(androidDocumentsDirectory, "Docucraft")

            val file =
                FileKit.openFileSaver(
                    suggestedName = scannedPdf.title ?: scannedPdf.filename,
                    extension = "pdf",
                    directory = dir,
                )
                    ?: run {
                        _eventFlow.emitEvent(UiEvent.SavingResult.Cancelled)
                        return@executeAsync Uri.EMPTY
                    }

            val internalPdf = PlatformFile(scannedPdf.path)
            internalPdf.copyTo(file)

            file.path.toUri()
        }
    }

    private fun handleScanningResult(activityResult: ActivityResult) {
        when (activityResult.resultCode) {
            Activity.RESULT_OK -> {
                val data = activityResult.data
                if (data == null) {
                    _eventFlow.emitEvent(
                        UiEvent.ScanResult.Failure(
                            error = IllegalStateException("Scan result intent data is null")
                        )
                    )
                    return
                }

                val scannerResult = GmsDocumentScanningResult.fromActivityResultIntent(data)
                if (scannerResult == null) {
                    _eventFlow.emitEvent(
                        UiEvent.ScanResult.Failure(
                            error = IllegalStateException("Scanner result could not be parsed")
                        )
                    )
                    return
                }

                val scannedPdf = scannerResult.pdf
                if (scannedPdf == null) {
                    _eventFlow.emitEvent(
                        UiEvent.ScanResult.Failure(
                            error = IllegalStateException("Scanned PDF is null")
                        )
                    )
                    return
                }

                saveScanResult(scannedPdf)
            }

            Activity.RESULT_CANCELED -> {
                logInfo("PDF scanning was cancelled by the user")
            }

            else -> {
                _eventFlow.emitEvent(
                    UiEvent.ScanResult.Failure(
                        error =
                            IllegalStateException(
                                "Unknown result code: ${activityResult.resultCode}"
                            )
                    )
                )
            }
        }
    }

    private fun saveScanResult(scannedPdf: GmsDocumentScanningResult.Pdf) {
        executeAsync(
            onSuccess = {
                logInfo("Scanned PDF saved successfully")
                _eventFlow.emitEvent(UiEvent.ScanResult.Success)
            },
            onError = { error ->
                logError("Failed to save the scanned PDF: ${error.message}", error)
                _eventFlow.emitEvent(UiEvent.ScanResult.Failure(error = error))
            }
        ) {
            val filename = "scan_${System.currentTimeMillis()}"
            saveScannedPdfUseCase(scannedPdf, filename)
        }
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
}
