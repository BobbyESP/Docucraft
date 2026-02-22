package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.viewmodel

import android.net.Uri
import android.os.Environment
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.domain.StringProvider
import com.bobbyesp.docucraft.core.domain.notifications.NotificationType
import com.bobbyesp.docucraft.core.presentation.common.Route
import com.bobbyesp.docucraft.core.util.events.UiEvent
import com.bobbyesp.docucraft.core.util.state.ResourceState
import com.bobbyesp.docucraft.core.util.state.ScreenState
import com.bobbyesp.docucraft.core.util.state.TemporalState
import com.bobbyesp.docucraft.core.util.viewModel.CoroutineBasedViewModel
import com.bobbyesp.docucraft.feature.docscanner.domain.FilterOptions
import com.bobbyesp.docucraft.feature.docscanner.domain.SortOption
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.DeleteDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.GetDocumentByIdUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.OpenDocumentInViewerUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SaveScannedDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ScanDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SearchDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ShareDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.UpdateDocumentFieldsUseCase
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeUiAction
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeUiEffect
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeUiState
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.copyTo
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
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
    private val observeDocumentsUseCase: ObserveDocumentsUseCase,
    private val searchDocumentsUseCase: SearchDocumentsUseCase,
    private val getDocumentByIdUseCase: GetDocumentByIdUseCase,
    private val saveScannedDocumentUseCase: SaveScannedDocumentUseCase,
    private val deleteDocumentUseCase: DeleteDocumentUseCase,
    private val updateDocumentFieldsUseCase: UpdateDocumentFieldsUseCase,
    private val openDocumentInViewerUseCase: OpenDocumentInViewerUseCase,
    private val shareDocumentUseCase: ShareDocumentUseCase,
    private val scanDocumentUseCase: ScanDocumentUseCase,
    private val stringProvider: StringProvider,
) : CoroutineBasedViewModel() {

    // State management
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _events: MutableSharedFlow<UiEvent>
    val events: SharedFlow<UiEvent>

    init {
        val eventsPair = createEventFlow<UiEvent>()
        _events = eventsPair.first
        events = eventsPair.second

        observeDocuments()
    }

    private fun observeDocuments() {
        viewModelScope.launch(Dispatchers.IO) {
            combine(
                flow { emitAll(observeDocumentsUseCase()) },
                _uiState.map { it.searchQuery }.distinctUntilChanged(),
                _uiState.map { it.filterOptions }.distinctUntilChanged(),
            ) { documents, query, filterOptions ->
                applyFiltersAndSort(documents, query, filterOptions)
            }
                .onStart { _uiState.updateValue { it.copy(fetchState = ScreenState.Loading()) } }
                .catch { error ->
                    logError("Failed to retrieve documents: ${error.message}", error)
                    _uiState.updateValue {
                        it.copy(fetchState = ScreenState.Error(stringProvider.getError(error)))
                    }
                    _events.emitEvent(UiEvent.ShowMessage(stringProvider.getError(error)))
                }
                .collect { (filteredList, isRepositoryEmpty) ->
                    _uiState.updateValue {
                        it.copy(
                            visibleDocuments = filteredList,
                            hasDocuments = !isRepositoryEmpty,
                            fetchState = if (isRepositoryEmpty) ScreenState.Idle() else ScreenState.Success(
                                Unit
                            ),
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
                // Fallback to in-memory filtering
                result =
                    result.filter { document ->
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

        return result to unfilteredDocuments.isEmpty()
    }

    // Events using Channel for one-time events
    private val _uiEffect = Channel<HomeUiEffect>(Channel.BUFFERED)
    val uiEffect = _uiEffect.receiveAsFlow()

    override fun onCoroutineException(throwable: Throwable) {
        viewModelScope.launch {
            _events.emitEvent(UiEvent.ShowMessage(
                stringProvider.getError(throwable)
            ))
        }
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

            // Document Operations
            is HomeUiAction.OpenDocument -> sendEvent(HomeUiEffect.Navigate(Route.PdfViewer(action.uri.toString())))
            is HomeUiAction.SaveDocument -> onExportDocument(action.document)
            is HomeUiAction.ShareDocument -> onShareDocument(action.uri)
            is HomeUiAction.DeleteDocument -> {
                _uiState.updateValue { it.copy(documentForRemoval = TemporalState.NotPresent) }
                action.id?.let { id ->
                    launchIO {
                        val scannedDocument = getDocumentByIdUseCase(id)
                        onDeleteDocument(scannedDocument.path)
                    }
                }
            }

            is HomeUiAction.UpdateDocumentFields -> {
                updateDocumentFields(action.id, action.title, action.description)
            }

            // Dialogs
            is HomeUiAction.ShowDeleteConfirmation -> {
                launchIO {
                    val scannedDocument = getDocumentByIdUseCase(action.id)
                    _uiState.updateValue {
                        it.copy(documentForRemoval = TemporalState.Present(scannedDocument))
                    }
                }
            }

            is HomeUiAction.ShowEditDialog -> {
                launchIO {
                    val scannedDocument = getDocumentByIdUseCase(action.id)
                    _uiState.updateValue {
                        it.copy(documentForModification = TemporalState.Present(scannedDocument))
                    }
                }
            }

            is HomeUiAction.ShowOptionsSheet -> {
                launchIO {
                    val scannedDocument = getDocumentByIdUseCase(action.id)
                    _uiState.updateValue {
                        it.copy(documentForActionMenu = TemporalState.Present(scannedDocument))
                    }
                }
            }

            HomeUiAction.DismissDialogs -> {
                _uiState.updateValue {
                    it.copy(
                        documentForRemoval = TemporalState.NotPresent,
                        documentForModification = TemporalState.NotPresent,
                        documentInfoCandidate = TemporalState.NotPresent,
                    )
                }
            }

            is HomeUiAction.DismissOptionsSheet -> {
                _uiState.updateValue { it.copy(documentForActionMenu = TemporalState.NotPresent) }
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
                    is ResourceState.Loading -> {
                        _uiState.updateValue { it.copy(isScanning = true) }
                    }

                    is ResourceState.Success -> {
                        val document = resource.data
                        _uiState.updateValue {
                            it.copy(isScanning = false, mostRecentScan = document)
                        }
                        // Auto-save logic
                        document?.let {
                            try {
                                val filename = "Scan_${System.currentTimeMillis()}"
                                saveScannedDocumentUseCase(it, filename)
                                _events.emitEvent(
                                    UiEvent.ShowMessage(
                                        stringProvider.get(
                                            R.string.doc_saved_successfully,
                                        ),
                                        type = NotificationType.Success
                                    )
                                )
                            } catch (e: Exception) {
                                _events.emitEvent(
                                    UiEvent.ShowMessage(
                                        message = stringProvider.get(
                                            id = R.string.doc_scan_error,
                                        ),
                                        type = NotificationType.Error
                                    )
                                )
                            }
                        }
                    }

                    is ResourceState.Error -> {
                        _uiState.updateValue {
                            it.copy(isScanning = false, scanUserMessage = resource.message)
                        }

                        _events.emitEvent(
                            UiEvent.ShowMessage(
                                message = resource.message ?: stringProvider.get(R.string.unknown_error),
                                type = NotificationType.Error
                            )
                        )
                    }
                }
            }
        }
    }

    private fun updateDocumentFields(documentId: String, title: String, description: String) {
        executeAsync(
            onSuccess = {
                logInfo("Document metadata updated successfully")
                _events.emitEvent(
                    UiEvent.ShowMessage(
                        message = stringProvider.get(R.string.doc_updated_successfully),
                        type = NotificationType.Success
                    )
                )
            },
            onError = { error ->
                logError("Failed to modify Document: ${error.message}", error)
                _events.emitEvent(
                    UiEvent.ShowMessage(
                        message = stringProvider.getError(error),
                        type = NotificationType.Error
                    )
                )
            },
        ) {
            val scannedDocument = getDocumentByIdUseCase(documentId)
            val newTitle: String? = title.ifBlank { null }
            val newDescription: String? = description.ifBlank { null }

            updateDocumentFieldsUseCase(scannedDocument.id, newTitle, newDescription)

            _uiState.updateValue { it.copy(documentForModification = TemporalState.NotPresent) }
        }
    }

    private fun onViewDocument(documentUri: Uri) {
        try {
            openDocumentInViewerUseCase(documentUri)
        } catch (e: Exception) {
            _events.emitEvent(
                UiEvent.ShowMessage(
                    message = stringProvider.get(R.string.issue_opening_doc_viewer),
                    type = NotificationType.Error
                )
            )
        }
    }

    private fun onShareDocument(documentUri: Uri) {
        try {
            shareDocumentUseCase(documentUri)
        } catch (e: Exception) {
            _events.emitEvent(
                UiEvent.ShowMessage(
                    message = stringProvider.get(R.string.issue_sharing_doc),
                    type = NotificationType.Error
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
                    type = NotificationType.Success
                )
            )
        } catch (e: Exception) {
            logError("Failed to delete Document: ${e.message}", e)
            _events.emitEvent(
                UiEvent.ShowMessage(
                    message = stringProvider.get(R.string.doc_delete_error),
                    type = NotificationType.Error
                )
            )
        }
    }

    private fun onExportDocument(scannedDocument: ScannedDocument) {
        executeAsync(
            onSuccess = { uri: Uri ->
                if (uri != Uri.EMPTY) {
                    logInfo("Document saved successfully to: $uri")
                    _events.emitEvent(
                        UiEvent.ShowMessage(
                            message = stringProvider.get(R.string.doc_saved_successfully_to, uri.path.toString()),
                            type = NotificationType.Success
                        )
                    )
                }
            },
            onError = { error ->
                logError("Failed to save document: ${error.message}", error)

                _events.emitEvent(
                    UiEvent.ShowMessage(
                        message = stringProvider.getError(id = R.string.doc_save_error_with_reason, throwable = error),
                        type = NotificationType.Error
                    )
                )
            },
        ) {
            val androidDocumentsDirectory =
                PlatformFile(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                )

            val dir = PlatformFile(androidDocumentsDirectory, "Docucraft")

            val file =
                FileKit.openFileSaver(
                    suggestedName = scannedDocument.title ?: scannedDocument.filename,
                    extension = "pdf",
                    directory = dir,
                )
                    ?: run {
                        return@executeAsync Uri.EMPTY
                    }

            val internalDocument = PlatformFile(scannedDocument.path)
            internalDocument.copyTo(file)

            file.path.toUri()
        }
    }
}
