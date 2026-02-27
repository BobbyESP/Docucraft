package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.domain.StringProvider
import com.bobbyesp.docucraft.core.domain.notifications.NotificationType
import com.bobbyesp.docucraft.core.domain.repository.AnalyticsHelper
import com.bobbyesp.docucraft.core.presentation.common.Route
import com.bobbyesp.docucraft.core.util.events.UiEvent
import com.bobbyesp.docucraft.core.util.viewModel.CoroutineBasedViewModel
import com.bobbyesp.docucraft.feature.docscanner.domain.FilterOptions
import com.bobbyesp.docucraft.feature.docscanner.domain.ScannerManager
import com.bobbyesp.docucraft.feature.docscanner.domain.SortOption
import com.bobbyesp.docucraft.feature.docscanner.domain.exception.DocumentExportFailure
import com.bobbyesp.docucraft.feature.docscanner.domain.model.RawScanResult
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.DeleteDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ExportDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.GetDocumentByIdUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.OpenDocumentInViewerUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SaveScannedDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SearchDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ShareDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeStatus
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeUiAction
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeUiEffect
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeUiState
import com.bobbyesp.docucraft.feature.shared.domain.BasicDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val scannerManager: ScannerManager,
    private val observeDocumentsUseCase: ObserveDocumentsUseCase,
    private val searchDocumentsUseCase: SearchDocumentsUseCase,
    private val getDocumentByIdUseCase: GetDocumentByIdUseCase,
    private val saveScannedDocumentUseCase: SaveScannedDocumentUseCase,
    private val deleteDocumentUseCase: DeleteDocumentUseCase,
    private val openDocumentInViewerUseCase: OpenDocumentInViewerUseCase,
    private val shareDocumentUseCase: ShareDocumentUseCase,
    private val exportDocumentUseCase: ExportDocumentUseCase,
    private val stringProvider: StringProvider,
    private val analyticsHelper: AnalyticsHelper,
) : CoroutineBasedViewModel() {

    override fun onCoroutineException(throwable: Throwable) {
        viewModelScope.launch {
            _events.emitEvent(UiEvent.ShowMessage(stringProvider.getError(throwable)))
        }
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _events: MutableSharedFlow<UiEvent>
    val events: SharedFlow<UiEvent>

    private fun sendEvent(effect: HomeUiEffect) {
        viewModelScope.launch { _uiEffect.send(effect) }
    }

    private val _uiEffect = Channel<HomeUiEffect>(Channel.BUFFERED)
    val uiEffect = _uiEffect.receiveAsFlow()

    init {
        val eventsPair = createEventFlow<UiEvent>()
        _events = eventsPair.first
        events = eventsPair.second

        observeDocuments()

        viewModelScope.launch {
            scannerManager.scanResult.collect { result ->
                result.onSuccess { scanResult ->
                    processScanResult(scanResult)
                }.onFailure {
                    _uiState.update { it.copy(isScanning = false) }
                    Log.w("HomeViewModel", "Failed to scan document: ${it.message}", it)
                }
            }
        }
    }

    fun onAction(action: HomeUiAction) {
        when (action) {
            HomeUiAction.LaunchDocumentScanner -> {
                launchDefault { scannerManager.requestScan() }
            }

            is HomeUiAction.ScanResultAction -> processScanResult(action.rawScanResult)

            is HomeUiAction.ViewDocument -> {
                launchIO {
                    val basicInformation = getBasicInformationForId(action.id)
                    sendEvent(HomeUiEffect.Navigate(Route.PdfViewer(documentInfo = basicInformation)))
                }
            }

            is HomeUiAction.SaveDocument -> onExportDocument(action.document)
            is HomeUiAction.ShareDocument -> onShareDocument(action.uri)

            is HomeUiAction.DeleteDocument -> {
                launchIO {
                    val doc = getDocumentByIdUseCase(action.id)
                    onDeleteDocument(doc.path)
                }
            }

            is HomeUiAction.UpdateSearchQuery ->
                _uiState.update { it.copy(searchQuery = action.query) }

            HomeUiAction.ClearSearch ->
                _uiState.update { it.copy(searchQuery = "") }

            is HomeUiAction.ToggleSearchBar ->
                _uiState.update { it.copy(isSearchBarVisible = action.isVisible) }

            is HomeUiAction.ApplySort ->
                _uiState.update { it.copy(filterOptions = it.filterOptions.copy(sortBy = action.sortOption)) }

            is HomeUiAction.ApplyFilter ->
                _uiState.update { it.copy(filterOptions = action.filterOptions) }

            HomeUiAction.ClearFilters ->
                _uiState.update { it.copy(filterOptions = FilterOptions.default) }
        }
    }

    private fun observeDocuments() {
        viewModelScope.launch(Dispatchers.IO) {
            combine(
                flow { emitAll(observeDocumentsUseCase()) },
                _uiState.map { it.searchQuery },
                _uiState.map { it.filterOptions },
            ) { documents, query, filterOptions ->
                applyFiltersAndSort(documents, query, filterOptions)
            }.onStart { _uiState.update { it.copy(status = HomeStatus.Loading) } }
                .catch { error ->
                    logError("Failed to retrieve documents: ${error.message}", error)
                    _uiState.update { it.copy(status = HomeStatus.Error(stringProvider.getError(error))) }
                    _events.emitEvent(UiEvent.ShowMessage(stringProvider.getError(error)))
                }.collect { (filteredList, isRepositoryEmpty) ->
                    _uiState.update {
                        it.copy(
                            visibleDocuments = filteredList,
                            hasDocuments = !isRepositoryEmpty,
                            status = HomeStatus.Idle,
                        )
                    }
                }
        }
    }

    private suspend fun applyFiltersAndSort(
        unfilteredDocuments: List<ScannedDocument>,
        query: String,
        filterOptions: FilterOptions,
    ): Pair<List<ScannedDocument>, Boolean> {
        var result = unfilteredDocuments

        if (query.isNotBlank()) {
            try {
                val searchResults = searchDocumentsUseCase(query)
                result = result.filter { document -> searchResults.any { it.id == document.id } }
            } catch (e: Exception) {
                logError("Search failed: ${e.message}", e)
                result = result.filter { document ->
                    document.title?.contains(query, ignoreCase = true) == true ||
                            document.filename.contains(query, ignoreCase = true) ||
                            document.description?.contains(query, ignoreCase = true) == true
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
            SortOption.Criteria.DATE ->
                if (filterOptions.sortBy.order == SortOption.Order.DESC)
                    result.sortedByDescending { it.createdTimestamp }
                else result.sortedBy { it.createdTimestamp }

            SortOption.Criteria.NAME ->
                if (filterOptions.sortBy.order == SortOption.Order.DESC)
                    result.sortedByDescending { it.title ?: it.filename }
                else result.sortedBy { it.title ?: it.filename }

            SortOption.Criteria.SIZE ->
                if (filterOptions.sortBy.order == SortOption.Order.DESC)
                    result.sortedByDescending { it.fileSize }
                else result.sortedBy { it.fileSize }
        }

        return result to unfilteredDocuments.isEmpty()
    }

    private fun processScanResult(rawScanResult: RawScanResult) {
        launchIO {
            _uiState.update { it.copy(isScanning = false) }
            saveScannedDocumentUseCase(rawScanResult)
                .onSuccess {
                    _events.emitEvent(
                        UiEvent.ShowMessage(
                            stringProvider.get(R.string.doc_saved_successfully),
                            type = NotificationType.Success,
                        )
                    )
                }.onFailure { error ->
                    logError("Failed to save document: ${error.message}", error)
                    _events.emitEvent(
                        UiEvent.ShowMessage(
                            message = stringProvider.get(R.string.doc_save_error_with_reason, error),
                            type = NotificationType.Error,
                        )
                    )
                }
        }
    }

    private suspend fun getBasicInformationForId(id: String): BasicDocument {
        val document = getDocumentByIdUseCase(pdfId = id)
        return BasicDocument(
            id = document.id,
            filename = document.filename,
            uri = document.path.toString(),
            title = document.title,
            description = document.description,
        )
    }

    private fun onShareDocument(documentUri: Uri) {
        try {
            shareDocumentUseCase(documentUri)
        } catch (e: Exception) {
            logError("Failed to share document: ${e.message}", e)
            _events.emitEvent(
                UiEvent.ShowMessage(
                    message = stringProvider.get(R.string.issue_sharing_doc),
                    type = NotificationType.Error,
                )
            )
        }
    }

    private suspend fun onDeleteDocument(documentUri: Uri) {
        try {
            deleteDocumentUseCase(documentUri)
            _events.emitEvent(
                UiEvent.ShowMessage(
                    message = stringProvider.get(R.string.doc_deleted_successfully),
                    type = NotificationType.Success,
                )
            )
        } catch (e: Exception) {
            logError("Failed to delete Document: ${e.message}", e)
            _events.emitEvent(
                UiEvent.ShowMessage(
                    message = stringProvider.get(R.string.doc_delete_error),
                    type = NotificationType.Error,
                )
            )
        }
    }

    private fun onExportDocument(scannedDocument: ScannedDocument) {
        launchSafe(context = Dispatchers.IO) {
            exportDocumentUseCase(scannedDocument)
                .onSuccess { uri ->
                    logInfo("Document saved successfully to: $uri")
                    _events.emitEvent(
                        UiEvent.ShowMessage(
                            message = stringProvider.get(R.string.doc_saved_successfully_to, uri),
                            type = NotificationType.Success,
                        )
                    )
                }.onFailure { error ->
                    logError("Failed to save document: ${error.message}", error)
                    if (error !is DocumentExportFailure.Cancelled) {
                        _events.emitEvent(
                            UiEvent.ShowMessage(
                                message = stringProvider.getError(
                                    id = R.string.doc_save_error_with_reason,
                                    throwable = error,
                                ),
                                type = NotificationType.Error,
                            )
                        )
                    }
                }
        }
    }
}
