package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.sheet

import androidx.lifecycle.viewModelScope
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.domain.StringProvider
import com.bobbyesp.docucraft.core.domain.notifications.NotificationType
import com.bobbyesp.docucraft.core.util.events.UiEvent
import com.bobbyesp.docucraft.core.util.viewModel.CoroutineBasedViewModel
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.GetDocumentByIdUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.UpdateDocumentFieldsUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel that owns all state and logic for the document bottom-sheet.
 *
 * Responsibilities:
 *  - Tracks which document is being shown (via a live [ObserveDocumentsUseCase] subscription
 *    so the header always reflects the latest persisted data).
 *  - Owns the edit-form fields ([DocumentSheetUiState.editTitle] / [editDescription]).
 *    These are plain ViewModel state — no Compose state escapes here — so they survive
 *    configuration changes, orientation flips and Sheet ↔ AlertDialog switches with zero
 *    workarounds.
 *  - Manages the in-sheet navigation backstack ([DocumentSheetUiState.pageStack]).
 *  - Delegates one-shot side-effects that require the parent's use-cases (share, save, delete)
 *    via [DocumentSheetEffect] so the sheet VM stays lean.
 */
class DocumentSheetViewModel(
    private val observeDocumentsUseCase: ObserveDocumentsUseCase,
    private val getDocumentByIdUseCase: GetDocumentByIdUseCase,
    private val updateDocumentFieldsUseCase: UpdateDocumentFieldsUseCase,
    private val stringProvider: StringProvider,
) : CoroutineBasedViewModel() {

    override fun onCoroutineException(throwable: Throwable) {
        viewModelScope.launch {
            _events.emitEvent(UiEvent.ShowMessage(stringProvider.getError(throwable)))
        }
    }

    private val _uiState = MutableStateFlow(DocumentSheetUiState())
    val uiState: StateFlow<DocumentSheetUiState> = _uiState.asStateFlow()

    private val _events: MutableSharedFlow<UiEvent>
    val events: SharedFlow<UiEvent>

    private val _effects = Channel<DocumentSheetEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        val pair = createEventFlow<UiEvent>()
        _events = pair.first
        events = pair.second

        observeActiveDocument()
    }

    fun onAction(action: DocumentSheetAction) {
        when (action) {
            is DocumentSheetAction.Open -> openSheet(action.documentId)

            DocumentSheetAction.NavigateToEdit -> push(SheetPage.Edit)
            DocumentSheetAction.NavigateToDelete -> push(SheetPage.Delete)

            DocumentSheetAction.Back -> {
                val stack = _uiState.value.pageStack
                if (stack.size <= 1) dismiss()
                else _uiState.update { it.copy(pageStack = stack.dropLast(1)) }
            }

            DocumentSheetAction.Dismiss -> dismiss()

            is DocumentSheetAction.UpdateTitle ->
                _uiState.update { it.copy(editTitle = action.value) }

            is DocumentSheetAction.UpdateDescription ->
                _uiState.update { it.copy(editDescription = action.value) }

            DocumentSheetAction.ConfirmEdit -> confirmEdit()

            DocumentSheetAction.ConfirmDelete -> {
                val id = _uiState.value.activeDocument?.id ?: return
                dismiss()
                viewModelScope.launch { _effects.send(DocumentSheetEffect.RequestDelete(id)) }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun openSheet(documentId: String) {
        launchIO {
            val doc = getDocumentByIdUseCase(documentId)
            _uiState.update {
                it.copy(
                    activeDocument = doc,
                    pageStack = listOf(SheetPage.Actions),
                    // Initialise edit fields with persisted values every time the
                    // sheet is freshly opened for a (possibly different) document.
                    editTitle = doc.title.orEmpty(),
                    editDescription = doc.description.orEmpty(),
                )
            }
        }
    }

    /**
     * Subscribes to the repository and keeps [DocumentSheetUiState.activeDocument] in sync.
     * This means the header (title, description, thumbnail) always shows the latest saved
     * data without any manual invalidation or re-fetch after an edit.
     *
     * Importantly, the edit-form fields ([editTitle] / [editDescription]) are NOT driven by
     * this flow — they are only set once when the sheet opens, so ongoing emissions never
     * overwrite what the user is currently typing.
     */
    private fun observeActiveDocument() {
        launchIO {
            observeDocumentsUseCase().collect { allDocs ->
                val currentId = _uiState.value.activeDocument?.id ?: return@collect
                val updated = allDocs.firstOrNull { it.id == currentId } ?: return@collect
                _uiState.update { it.copy(activeDocument = updated) }
            }
        }
    }

    private fun confirmEdit() {
        val state = _uiState.value
        val doc = state.activeDocument ?: return
        if (!state.canConfirmEdit) return

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
            val newTitle: String? = state.editTitle.trim().ifBlank { null }
            val newDescription: String? = state.editDescription.trim().ifBlank { null }

            updateDocumentFieldsUseCase(doc.id, newTitle, newDescription)

            // Navigate back to Actions only after the DB write succeeds,
            // so the header already reflects the new values when it appears.
            _uiState.update { current ->
                current.copy(
                    pageStack = current.pageStack.dropLastWhile { it is SheetPage.Edit }
                        .ifEmpty { listOf(SheetPage.Actions) }
                )
            }
        }
    }

    private fun push(page: SheetPage) {
        _uiState.update { it.copy(pageStack = it.pageStack + page) }
    }

    private fun dismiss() {
        _uiState.update {
            it.copy(
                activeDocument = null,
                pageStack = emptyList(),
            )
        }
    }

    // Helpers for share / save — emit effects so HomeViewModel handles them
    fun onRequestShare() {
        val id = _uiState.value.activeDocument?.id ?: return
        viewModelScope.launch { _effects.send(DocumentSheetEffect.RequestShare(id)) }
    }

    fun onRequestSave() {
        val id = _uiState.value.activeDocument?.id ?: return
        viewModelScope.launch { _effects.send(DocumentSheetEffect.RequestSave(id)) }
    }
}




