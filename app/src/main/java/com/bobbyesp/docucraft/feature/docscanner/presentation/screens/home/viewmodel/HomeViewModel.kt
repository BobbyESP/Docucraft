package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.domain.StringProvider
import com.bobbyesp.docucraft.core.domain.analytics.AnalyticsEvent
import com.bobbyesp.docucraft.core.domain.notifications.NotificationType
import com.bobbyesp.docucraft.core.domain.repository.AnalyticsHelper
import com.bobbyesp.docucraft.core.presentation.navigation.Route
import com.bobbyesp.docucraft.core.util.events.UiEvent
import com.bobbyesp.docucraft.core.util.viewModel.BaseViewModel
import com.bobbyesp.docucraft.feature.docscanner.domain.FilterOptions
import com.bobbyesp.docucraft.feature.docscanner.domain.ScannerManager
import com.bobbyesp.docucraft.feature.docscanner.domain.model.RawScanResult
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.DeleteDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ExportDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.FilterDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.GetDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SaveScannedDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SearchDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ShareDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SortDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.UpdateDocumentFieldsUseCase
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeEffect
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeIntent
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeStatus
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeUiState
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.sheet.DocumentSheetUiState
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.sheet.SheetAction
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.sheet.SheetPage
import com.bobbyesp.docucraft.feature.shared.domain.BasicDocument
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

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
) : BaseViewModel<HomeIntent, HomeUiState, HomeEffect>(
    initialState = HomeUiState()
) {

    init {
        observeDocuments()
        observeScanner()
    }

    // ---------------- INTENTS ----------------

    override fun onHandleIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.Load -> observeDocuments()

            HomeIntent.LaunchScanner -> launch {
                analyticsHelper.logEvent(AnalyticsEvent(AnalyticsEvent.Types.SCAN_STARTED))
                scannerManager.requestScan()
            }

            is HomeIntent.ScanResult -> processScanResult(intent.result)

            is HomeIntent.ViewDocument -> openDocument(intent.id)

            is HomeIntent.UpdateSearch -> {
                if (intent.query.length >= 3 && intent.query != currentState.searchQuery) {
                    analyticsHelper.logEvent(
                        AnalyticsEvent(
                            type = AnalyticsEvent.Types.SEARCH_PERFORMED,
                            extras = listOf(
                                AnalyticsEvent.Param(
                                    AnalyticsEvent.ParamKeys.QUERY_LENGTH,
                                    intent.query.length.toString()
                                )
                            )
                        )
                    )
                }
                setState { copy(searchQuery = intent.query) }
            }

            HomeIntent.ClearSearch ->
                setState { copy(searchQuery = "") }

            is HomeIntent.ToggleSearch ->
                setState { copy(isSearchBarVisible = intent.visible) }

            is HomeIntent.ApplySort -> {
                analyticsHelper.logEvent(
                    AnalyticsEvent(
                        type = AnalyticsEvent.Types.FILTER_APPLIED,
                        extras = listOf(
                            AnalyticsEvent.Param(
                                AnalyticsEvent.ParamKeys.SORT_BY,
                                "${intent.sort.criteria.name}_${intent.sort.order.name}"
                            )
                        )
                    )
                )
                setState { copy(filterOptions = filterOptions.copy(sortBy = intent.sort)) }
            }

            is HomeIntent.ApplyFilter -> {
                analyticsHelper.logEvent(
                    AnalyticsEvent(
                        type = AnalyticsEvent.Types.FILTER_APPLIED,
                        extras = listOf(
                            AnalyticsEvent.Param(
                                AnalyticsEvent.ParamKeys.FILTER_TYPE,
                                "complex_filter"
                            )
                        )
                    )
                )
                setState { copy(filterOptions = intent.filter) }
            }

            HomeIntent.ClearFilters ->
                setState { copy(filterOptions = FilterOptions.default) }

            is HomeIntent.OpenSheet -> openSheet(intent.id)
            HomeIntent.DismissSheet -> dismissSheet()

            is HomeIntent.Sheet -> handleSheet(intent.action)
        }
    }

    // ---------------- OBSERVE ----------------

    private fun observeDocuments() = launch {
        combine(
            observeDocumentsUseCase(),
            state.map { it.searchQuery },
            state.map { it.filterOptions }
        ) { docs, query, filters ->
            val searched = searchDocumentsUseCase(docs, query)
            val filtered = filterDocumentsUseCase(searched, filters)
            val sorted = sortDocumentsUseCase(filtered, filters.sortBy)

            Pair(
                sorted,
                docs.isNotEmpty()
            )
        }
            .onStart {
                setState { copy(status = HomeStatus.Loading) }
            }
            .catch { error ->
                setState {
                    copy(status = HomeStatus.Error(stringProvider.getError(error)))
                }
                sendUiEvent(
                    UiEvent.ShowMessage(
                        stringProvider.getError(error),
                        NotificationType.Error
                    )
                )
            }
            .collect { (sorted, hasDocuments) ->
                setState {
                    copy(
                        visibleDocuments = sorted,
                        hasDocuments = hasDocuments,
                        status = HomeStatus.Idle
                    )
                }
            }
    }

    private fun observeScanner() = launch {
        scannerManager.scanResult.collect { result ->
            result
                .onSuccess { processScanResult(it) }
                .onFailure {
                    analyticsHelper.logEvent(
                        AnalyticsEvent(
                            type = AnalyticsEvent.Types.SCAN_CANCELLED,
                            extras = listOf(
                                AnalyticsEvent.Param(
                                    AnalyticsEvent.ParamKeys.STATUS,
                                    it.message ?: "unknown"
                                )
                            )
                        )
                    )
                    setState { copy(isScanning = false) }
                }
        }
    }

    // ---------------- ACTIONS ----------------

    private fun openDocument(uuid: String) = launch {
        val doc = getDocumentUseCase(uuid)

        sendEffect(
            HomeEffect.Navigate(
                Route.PdfViewer(
                    documentInfo = BasicDocument(
                        uuid = doc.uuid,
                        filename = doc.filename,
                        uri = doc.path.toString(),
                        title = doc.title,
                        description = doc.description
                    )
                )
            )
        )
    }

    private fun processScanResult(result: RawScanResult) = launch(
        onError = {
            sendUiEvent(
                UiEvent.ShowMessage(
                    stringProvider.getError(it),
                    NotificationType.Error
                )
            )
        }
    ) {
        setState { copy(isScanning = false) }

        saveScannedDocumentUseCase(result)

        analyticsHelper.logEvent(
            AnalyticsEvent(
                type = AnalyticsEvent.Types.SCAN_COMPLETED,
                extras = listOf(
                    AnalyticsEvent.Param(
                        AnalyticsEvent.ParamKeys.PAGE_COUNT,
                        result.pageCount.toString()
                    )
                )
            )
        )

        sendUiEvent(
            UiEvent.ShowMessage(
                stringProvider.get(R.string.doc_saved_successfully),
                NotificationType.Success
            )
        )
    }

    // ---------------- SHEET ----------------

    private fun handleSheet(action: SheetAction) {
        when (action) {
            SheetAction.Dismiss -> dismissSheet()

            SheetAction.Back -> {
                val stack = currentState.sheetState?.pageStack ?: return
                if (stack.size <= 1) dismissSheet()
                else updateSheet { it.copy(pageStack = stack.dropLast(1)) }
            }

            SheetAction.ConfirmDelete -> deleteCurrentDocument()

            SheetAction.ConfirmEdit -> confirmEdit()

            is SheetAction.Navigate ->
                updateSheet { it.copy(pageStack = it.pageStack + action.page) }

            SheetAction.RequestSave -> exportCurrent()

            SheetAction.RequestShare -> shareCurrent()

            is SheetAction.UpdateTitle ->
                updateSheet { it.copy(editTitle = action.value) }

            is SheetAction.UpdateDescription ->
                updateSheet { it.copy(editDescription = action.value) }
        }
    }

    private fun openSheet(uuid: String) = launch {
        savedStateHandle["active_sheet_doc_id"] = uuid

        val doc = getDocumentUseCase(uuid)

        setState {
            copy(
                sheetState = DocumentSheetUiState(
                    activeDocument = doc,
                    pageStack = listOf(SheetPage.Actions),
                    editTitle = doc.title.orEmpty(),
                    editDescription = doc.description.orEmpty()
                )
            )
        }
    }

    private fun dismissSheet() {
        savedStateHandle["active_sheet_doc_id"] = null
        setState { copy(sheetState = null) }
    }

    private fun updateSheet(transform: (DocumentSheetUiState) -> DocumentSheetUiState) {
        setState {
            copy(sheetState = sheetState?.let(transform))
        }
    }

    // ---------------- DOMAIN OPS ----------------

    private fun deleteCurrentDocument() = launch {
        val doc = currentState.sheetState?.activeDocument ?: return@launch

        deleteDocumentUseCase(doc.path)

        analyticsHelper.logEvent(
            AnalyticsEvent(
                type = AnalyticsEvent.Types.DOCUMENT_DELETED
            )
        )

        sendUiEvent(
            UiEvent.ShowMessage(
                stringProvider.get(R.string.doc_deleted_successfully),
                NotificationType.Success
            )
        )

        dismissSheet()
    }

    private fun shareCurrent() {
        val doc = currentState.sheetState?.activeDocument ?: return

        runCatching {
            shareDocumentUseCase(doc.path)
            analyticsHelper.logEvent(
                AnalyticsEvent(
                    type = AnalyticsEvent.Types.DOCUMENT_SHARED
                )
            )
        }.onFailure {
            sendUiEvent(
                UiEvent.ShowMessage(
                    stringProvider.get(R.string.issue_sharing_doc),
                    NotificationType.Error
                )
            )
        }
    }

    private fun exportCurrent() = launch {
        val doc = currentState.sheetState?.activeDocument ?: return@launch

        exportDocumentUseCase(doc)
            .onSuccess { uri ->
                analyticsHelper.logEvent(
                    AnalyticsEvent(
                        type = AnalyticsEvent.Types.DOCUMENT_EXPORTED
                    )
                )
                sendUiEvent(
                    UiEvent.ShowMessage(
                        stringProvider.get(R.string.doc_saved_successfully_to, uri),
                        NotificationType.Success
                    )
                )
            }
            .onFailure {
                sendUiEvent(
                    UiEvent.ShowMessage(
                        stringProvider.getError(it),
                        NotificationType.Error
                    )
                )
            }
    }

    private fun confirmEdit() = launch {
        val sheet = currentState.sheetState ?: return@launch
        val doc = sheet.activeDocument ?: return@launch

        updateDocumentFieldsUseCase(
            doc.uuid,
            sheet.editTitle.trim().ifBlank { null },
            sheet.editDescription.trim().ifBlank { null }
        )

        sendUiEvent(
            UiEvent.ShowMessage(
                stringProvider.get(R.string.doc_updated_successfully),
                NotificationType.Success
            )
        )

        updateSheet {
            it.copy(pageStack = listOf(SheetPage.Actions))
        }
    }
}
