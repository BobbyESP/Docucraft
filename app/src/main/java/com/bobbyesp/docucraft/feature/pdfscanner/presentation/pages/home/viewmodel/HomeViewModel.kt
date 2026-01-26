package com.bobbyesp.docucraft.feature.pdfscanner.presentation.pages.home.viewmodel

import android.net.Uri
import android.os.Environment
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import com.bobbyesp.docucraft.core.util.Resource
import com.bobbyesp.docucraft.core.util.state.TemporalState
import com.bobbyesp.docucraft.core.util.viewModel.CoroutineBasedViewModel
import com.bobbyesp.docucraft.feature.pdfscanner.domain.FilterOptions
import com.bobbyesp.docucraft.feature.pdfscanner.domain.SortOption
import com.bobbyesp.docucraft.feature.pdfscanner.domain.model.ScannedPdf
import com.bobbyesp.docucraft.feature.pdfscanner.domain.usecase.*
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.contract.HomeUiAction
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.contract.HomeUiEffect
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.contract.HomeUiState
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.contract.LoadState
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.copyTo
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Home screen. Each use case has a single responsibility, following SOLID
 * principles.
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
    private val scanDocumentUseCase: ScanDocumentUseCase,
) : CoroutineBasedViewModel() {

    // State management
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observePdfs()
    }

    private fun observePdfs() {
        viewModelScope.launch(Dispatchers.IO) {
            combine(
                    flow { emitAll(getAllScannedPdfsUseCase()) },
                    _uiState.map { it.searchQuery }.distinctUntilChanged(),
                    _uiState.map { it.filterOptions }.distinctUntilChanged(),
                ) { pdfs, query, filterOptions ->
                    filterAndSortPdfs(pdfs, query, filterOptions)
                }
                .onStart { _uiState.updateValue { it.copy(loadState = LoadState.Loading) } }
                .catch { error ->
                    logError("Failed to retrieve PDFs: ${error.message}", error)
                    _uiState.updateValue {
                        it.copy(loadState = LoadState.Error(error.message ?: "Unknown error"))
                    }
                    sendEvent(HomeUiEffect.ShowError(error))
                }
                .collect { (filteredList, isRepositoryEmpty) ->
                    _uiState.updateValue {
                        it.copy(
                            scannedPdfs = filteredList,
                            isRepositoryEmpty = isRepositoryEmpty,
                            loadState = LoadState.Idle,
                        )
                    }
                }
        }
    }

    private suspend fun filterAndSortPdfs(
        originalPdfsList: List<ScannedPdf>,
        query: String,
        filterOptions: FilterOptions,
    ): Pair<List<ScannedPdf>, Boolean> {
        var result = originalPdfsList

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

        return result to originalPdfsList.isEmpty()
    }

    // Events using Channel for one-time events
    private val _uiEffect = Channel<HomeUiEffect>(Channel.BUFFERED)
    val uiEffect = _uiEffect.receiveAsFlow()

    override fun onCoroutineException(throwable: Throwable) {
        sendEvent(HomeUiEffect.ShowError(throwable))
    }

    private fun sendEvent(effect: HomeUiEffect) {
        viewModelScope.launch { _uiEffect.send(effect) }
    }

    fun onAction(action: HomeUiAction) {
        when (action) {
            // Scanning
            HomeUiAction.OnScanButtonClicked -> {
                sendEvent(HomeUiEffect.LaunchScanner)
            }

            is HomeUiAction.OnScanResultReceived -> processScanResult(action.result)
            is HomeUiAction.OnScanErrorDismissed -> {
                _uiState.updateValue { it.copy(scanUserMessage = null) }
            }

            // PDF Operations
            is HomeUiAction.OpenPdf -> openPdfInViewer(action.uri)
            is HomeUiAction.SavePdf -> copyPdfToDirectory(action.pdf)
            is HomeUiAction.SharePdf -> sharePdf(action.uri)
            is HomeUiAction.DeletePdf -> {
                _uiState.updateValue { it.copy(pdfToBeRemoved = TemporalState.NotPresent) }
                action.id?.let { id ->
                    launchIO {
                        val scannedPdf = getPdfById(id)
                        deletePdf(scannedPdf.path)
                    }
                }
            }

            is HomeUiAction.UpdatePdfMetadata -> {
                modifyPdfMetadata(action.id, action.title, action.description)
            }

            // Dialogs
            is HomeUiAction.ShowDeleteConfirmation -> {
                launchIO {
                    val scannedPdf = getPdfById(action.id)
                    _uiState.updateValue {
                        it.copy(pdfToBeRemoved = TemporalState.Present(scannedPdf))
                    }
                }
            }

            is HomeUiAction.ShowEditDialog -> {
                launchIO {
                    val scannedPdf = getPdfById(action.id)
                    _uiState.updateValue {
                        it.copy(pdfToBeModified = TemporalState.Present(scannedPdf))
                    }
                }
            }

            is HomeUiAction.ShowPdfInfo -> {
                sendEvent(HomeUiEffect.ShowPdfInfoDialog(action.id))
            }

            is HomeUiAction.ShowOptionsSheet -> {
                launchIO {
                    val scannedPdf = getPdfById(action.id)
                    _uiState.updateValue {
                        it.copy(pdfForOptions = TemporalState.Present(scannedPdf))
                    }
                }
            }

            HomeUiAction.DismissDialogs -> {
                _uiState.updateValue {
                    it.copy(
                        pdfToBeRemoved = TemporalState.NotPresent,
                        pdfToBeModified = TemporalState.NotPresent,
                        pdfToShowInformation = TemporalState.NotPresent,
                    )
                }
            }

            is HomeUiAction.DismissOptionsSheet -> {
                _uiState.updateValue { it.copy(pdfForOptions = TemporalState.NotPresent) }
            }

            // Search & Filter
            is HomeUiAction.UpdateSearchQuery -> {
                _uiState.updateValue { it.copy(searchQuery = action.query) }
            }

            HomeUiAction.ClearSearch -> {
                _uiState.updateValue { it.copy(searchQuery = "") }
            }

            is HomeUiAction.ToggleSearchBar -> {
                _uiState.updateValue { it.copy(isSearchBarVisible = action.isVisible) }
            }

            is HomeUiAction.ApplySort -> {
                _uiState.updateValue {
                    it.copy(filterOptions = it.filterOptions.copy(sortBy = action.sortOption))
                }
            }

            is HomeUiAction.ApplyFilter -> {
                _uiState.updateValue { it.copy(filterOptions = action.filterOptions) }
            }

            HomeUiAction.ClearFilters -> {
                _uiState.updateValue { it.copy(filterOptions = FilterOptions.default) }
            }
        }
    }

    private fun processScanResult(result: Any) {
        launchIO {
            scanDocumentUseCase(result).collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        _uiState.updateValue { it.copy(isScanning = true) }
                    }

                    is Resource.Success -> {
                        val document = resource.data
                        _uiState.updateValue {
                            it.copy(isScanning = false, lastScannedDocument = document)
                        }
                        // Auto-save logic
                        document?.let {
                            try {
                                val filename = "Scan_${System.currentTimeMillis()}"
                                saveScannedPdfUseCase(it, filename)
                                sendEvent(HomeUiEffect.ScanSuccess)
                            } catch (e: Exception) {
                                sendEvent(HomeUiEffect.ScanFailure(e))
                            }
                        }
                    }

                    is Resource.Error -> {
                        _uiState.updateValue {
                            it.copy(isScanning = false, scanUserMessage = resource.message)
                        }
                        sendEvent(
                            HomeUiEffect.ScanFailure(resource.error ?: Exception(resource.message))
                        )
                    }

                    else -> {
                        /* Ignore other states */
                    }
                }
            }
        }
    }

    private suspend fun getPdfById(pdfId: String): ScannedPdf {
        return getScannedPdfByIdUseCase(pdfId)
    }

    private fun modifyPdfMetadata(pdfId: String, title: String, description: String) {
        executeAsync(
            onSuccess = {
                logInfo("PDF metadata updated successfully")
                sendEvent(
                    HomeUiEffect.ScanSuccess
                ) // Reusing ScanSuccess as generic success or create new one
            },
            onError = { error ->
                logError("Failed to modify PDF: ${error.message}", error)
                sendEvent(HomeUiEffect.ShowError(error))
            },
        ) {
            val scannedPdf = getPdfById(pdfId)
            val newTitle: String? = title.ifBlank { null }
            val newDescription: String? = description.ifBlank { null }

            updatePdfMetadataUseCase(scannedPdf.id, newTitle, newDescription)

            _uiState.updateValue { it.copy(pdfToBeModified = TemporalState.NotPresent) }
        }
    }

    private fun openPdfInViewer(pdfPath: Uri) {
        try {
            openPdfInViewerUseCase(pdfPath)
        } catch (e: Exception) {
            sendEvent(HomeUiEffect.OpenPdfViewerFailure(error = e))
        }
    }

    private fun sharePdf(pdfPath: Uri) {
        try {
            sharePdfUseCase(pdfPath)
        } catch (e: Exception) {
            sendEvent(HomeUiEffect.SharePdfFailure(error = e))
        }
    }

    private suspend fun deletePdf(pdfPath: Uri) {
        try {
            deleteScannedPdfUseCase(pdfPath)
            sendEvent(HomeUiEffect.DeleteSuccess)
        } catch (e: Exception) {
            logError("Failed to delete PDF: ${e.message}", e)
            sendEvent(HomeUiEffect.DeleteFailure(error = e))
        }
    }

    private fun copyPdfToDirectory(scannedPdf: ScannedPdf) {
        executeAsync(
            onSuccess = { uri: Uri ->
                if (uri != Uri.EMPTY) {
                    logInfo("PDF saved successfully to: $uri")
                    sendEvent(HomeUiEffect.SaveSuccess(uri))
                }
            },
            onError = { error ->
                logError("Failed to save PDF: ${error.message}", error)
                sendEvent(HomeUiEffect.SaveFailure(error = error))
            },
        ) {
            val androidDocumentsDirectory =
                PlatformFile(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                )

            val dir = PlatformFile(androidDocumentsDirectory, "Docucraft")

            val file =
                FileKit.openFileSaver(
                    suggestedName = scannedPdf.title ?: scannedPdf.filename,
                    extension = "pdf",
                    directory = dir,
                )
                    ?: run {
                        return@executeAsync Uri.EMPTY
                    }

            val internalPdf = PlatformFile(scannedPdf.path)
            internalPdf.copyTo(file)

            file.path.toUri()
        }
    }
}
