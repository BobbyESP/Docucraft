package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.domain.StringProvider
import com.bobbyesp.docucraft.core.domain.notifications.NotificationType
import com.bobbyesp.docucraft.core.domain.repository.AnalyticsHelper
import com.bobbyesp.docucraft.core.presentation.navigation.Route
import com.bobbyesp.docucraft.core.util.events.UiEvent
import com.bobbyesp.docucraft.core.util.viewModel.CoroutineBasedViewModel
import com.bobbyesp.docucraft.feature.docscanner.domain.FilterOptions
import com.bobbyesp.docucraft.feature.docscanner.domain.ScannerManager
import com.bobbyesp.docucraft.feature.docscanner.domain.exception.DocumentExportFailure
import com.bobbyesp.docucraft.feature.docscanner.domain.model.RawScanResult
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.DeleteDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ExportDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.FilterDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SaveScannedDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SearchDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ShareDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SortDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.GetDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.UpdateDocumentFieldsUseCase
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeStatus
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeUiAction
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeUiEffect
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeUiState
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.sheet.DocumentSheetUiState
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.sheet.SheetAction
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.sheet.SheetPage
import com.bobbyesp.docucraft.feature.shared.domain.BasicDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val savedStateHandle: SavedStateHandle,
    private val scannerManager: ScannerManager,
    private val observeDocumentsUseCase: ObserveDocumentsUseCase,
    private val searchDocumentsUseCase: SearchDocumentsUseCase,
    private val filterDocumentsUseCase: FilterDocumentsUseCase,
    private val sortDocumentsUseCase: SortDocumentsUseCase,
    private val getDocumentUseCase: GetDocumentUseCase,
    private val saveScannedDocumentUseCase: SaveScannedDocumentUseCase,
    private val deleteDocumentUseCase: DeleteDocumentUseCase,
    private val shareDocumentUseCase: ShareDocumentUseCase,
    private val exportDocumentUseCase: ExportDocumentUseCase,
    private val updateDocumentFieldsUseCase: UpdateDocumentFieldsUseCase,
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

    private fun sendEvent(effect: HomeUiEffect) {
        viewModelScope.launch { _uiEffect.send(effect) }
    }

    private val _uiEffect = Channel<HomeUiEffect>(Channel.BUFFERED)
    val uiEffect = _uiEffect.receiveAsFlow()

    init {
        observeDocuments()

        savedStateHandle.get<String>(KEY_ACTIVE_SHEET_DOC_ID)?.let { id ->
            openSheet(id)
        }

        viewModelScope.launch {
            scannerManager.scanResult.collect { result ->
                result.onSuccess { scanResult ->
                    processScanResult(scanResult)
                }.onFailure { error ->
                    _uiState.update { it.copy(isScanning = false) }
                    Log.w("HomeViewModel", "Failed to scan document: ${error.message}", error)
                }
            }
        }
    }

    companion object {
        private const val KEY_ACTIVE_SHEET_DOC_ID = "active_sheet_doc_id"
    }

    fun onSheetAction(action: SheetAction) {
        when (action) {
            SheetAction.Dismiss -> dismissSheet()
            SheetAction.Back -> {
                val stack = _uiState.value.sheetState?.pageStack ?: return
                if (stack.size <= 1) dismissSheet()
                else updateSheet { it.copy(pageStack = stack.dropLast(1)) }
            }

            SheetAction.ConfirmDelete -> {
                val uuid = _uiState.value.sheetState?.activeDocument?.uuid ?: return
                dismissSheet()
                launchIO {
                    val doc = getDocumentUseCase(uuid)
                    onDeleteDocument(doc.path)
                }
            }

            SheetAction.ConfirmEdit -> confirmSheetEdit()
            is SheetAction.Navigate -> {
                updateSheet { it.copy(pageStack = it.pageStack + action.page) }
            }

            SheetAction.RequestSave -> {
                val doc = _uiState.value.sheetState?.activeDocument ?: return
                onExportDocument(doc)
            }

            SheetAction.RequestShare -> {
                val doc = _uiState.value.sheetState?.activeDocument ?: return
                onShareDocument(doc.path)
            }

            is SheetAction.UpdateDescription -> {
                updateSheet { it.copy(editDescription = action.value) }
            }

            is SheetAction.UpdateTitle -> {
                updateSheet { it.copy(editTitle = action.value) }
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
                    val basicInformation = getBasicInformationForUuid(action.id)
                    sendEvent(HomeUiEffect.Navigate(Route.PdfViewer(documentInfo = basicInformation)))
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

            is HomeUiAction.OpenSheet -> openSheet(action.documentId)
        }
    }

    private fun observeDocuments() {
        viewModelScope.launch(Dispatchers.IO) {
            combine(
                flow { emitAll(observeDocumentsUseCase()) },
                _uiState.map { it.searchQuery },
                _uiState.map { it.filterOptions },
            ) { documents, query, filterOptions ->
                val searched = searchDocumentsUseCase(documents, query)
                val filtered = filterDocumentsUseCase(searched, filterOptions)
                val sorted = sortDocumentsUseCase(filtered, filterOptions.sortBy)

                Pair(
                    documents.isEmpty(), sorted
                )
            }.onStart { _uiState.update { it.copy(status = HomeStatus.Loading) } }
                .catch { error ->
                    logError("Failed to retrieve documents: ${error.message}", error)
                    _uiState.update {
                        it.copy(
                            status = HomeStatus.Error(
                                stringProvider.getError(
                                    error
                                )
                            )
                        )
                    }
                    _events.emitEvent(UiEvent.ShowMessage(stringProvider.getError(error)))
                }.collect { (isRepositoryEmpty, sortedDocuments) ->
                    _uiState.update { state ->
                        // Keep the sheet's active document in sync with the latest DB data.
                        val updatedSheet = state.sheetState?.let { sheet ->
                            val refreshed =
                                sortedDocuments.firstOrNull { it.uuid == sheet.activeDocument?.uuid }
                                    ?: sheet.activeDocument
                            sheet.copy(activeDocument = refreshed)
                        }
                        state.copy(
                            visibleDocuments = sortedDocuments,
                            hasDocuments = !isRepositoryEmpty,
                            status = HomeStatus.Idle,
                            sheetState = updatedSheet,
                        )
                    }
                }
        }
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
                            message = stringProvider.get(
                                R.string.doc_save_error_with_reason,
                                error
                            ),
                            type = NotificationType.Error,
                        )
                    )
                }
        }
    }

    private suspend fun getBasicInformationForUuid(uuid: String): BasicDocument {
        val document = getDocumentUseCase(uuid)
        return BasicDocument(
            uuid = document.uuid,
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

    // -------------------------------------------------------------------------
    // Sheet helpers
    // -------------------------------------------------------------------------

    private fun openSheet(documentId: String) {
        savedStateHandle[KEY_ACTIVE_SHEET_DOC_ID] = documentId
        launchIO {
            val doc = getDocumentUseCase(documentId)
            _uiState.update {
                it.copy(
                    sheetState = DocumentSheetUiState(
                        activeDocument = doc,
                        pageStack = listOf(SheetPage.Actions),
                        editTitle = doc.title.orEmpty(),
                        editDescription = doc.description.orEmpty(),
                    )
                )
            }
        }
    }

    private fun dismissSheet() {
        savedStateHandle[KEY_ACTIVE_SHEET_DOC_ID] = null
        _uiState.update { it.copy(sheetState = null) }
    }

    /** Applies a transformation only when the sheet is currently open. */
    private fun updateSheet(transform: (DocumentSheetUiState) -> DocumentSheetUiState) {
        _uiState.update { state ->
            state.copy(sheetState = state.sheetState?.let(transform))
        }
    }

    private fun confirmSheetEdit() {
        val sheet = _uiState.value.sheetState ?: return
        val doc = sheet.activeDocument ?: return
        if (!sheet.canConfirmEdit) return

        executeAsync(
            onSuccess = {
                logInfo("Document fields updated successfully")
                _events.emitEvent(
                    UiEvent.ShowMessage(
                        message = stringProvider.get(R.string.doc_updated_successfully),
                        type = NotificationType.Success,
                    )
                )
            },
            onError = { error ->
                logError("Failed to update document fields: ${error.message}", error)
                _events.emitEvent(
                    UiEvent.ShowMessage(
                        message = stringProvider.getError(error),
                        type = NotificationType.Error,
                    )
                )
            },
        ) {
            val newTitle = sheet.editTitle.trim().ifBlank { null }
            val newDescription = sheet.editDescription.trim().ifBlank { null }
            updateDocumentFieldsUseCase(doc.uuid, newTitle, newDescription)
            updateSheet { current ->
                current.copy(
                    pageStack = current.pageStack
                        .dropLastWhile { it is SheetPage.Edit }
                        .ifEmpty { listOf(SheetPage.Actions) }
                )
            }
        }
    }
}
