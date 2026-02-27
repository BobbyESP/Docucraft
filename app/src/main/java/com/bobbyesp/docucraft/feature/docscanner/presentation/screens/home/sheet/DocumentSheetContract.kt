package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.sheet

import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

/**
 * Represents the page currently shown inside the bottom-sheet NavDisplay.
 * Using a sealed interface (instead of embedding the full document) keeps
 * the backstack entries stable — they never change identity, only the
 * ViewModel's [DocumentSheetUiState.activeDocument] field updates reactively.
 */
sealed interface SheetPage {
    data object Actions : SheetPage
    data object Edit : SheetPage
    data object Delete : SheetPage
}

data class DocumentSheetUiState(
    /** The document being acted on. Null means the sheet should not be visible. */
    val activeDocument: ScannedDocument? = null,
    /** The navigation stack inside the sheet. */
    val pageStack: List<SheetPage> = emptyList(),
    /** Edit-form state, kept here so it survives Sheet ↔ AlertDialog switches. */
    val editTitle: String = "",
    val editDescription: String = "",
) {
    val isVisible: Boolean get() = activeDocument != null && pageStack.isNotEmpty()
    val activePage: SheetPage? get() = pageStack.lastOrNull()

    val isTitleError: Boolean get() = editTitle.length > TITLE_MAX_LENGTH
    val isDescriptionError: Boolean get() = editDescription.length > DESCRIPTION_MAX_LENGTH
    val canConfirmEdit: Boolean get() = !isTitleError && !isDescriptionError

    /** Convenience projection consumed by the Edit composables. */
    val editUiState: EditDocumentUiState get() = EditDocumentUiState(
        title = editTitle,
        description = editDescription,
        isTitleError = isTitleError,
        isDescriptionError = isDescriptionError,
        canConfirm = canConfirmEdit,
    )

    companion object {
        const val TITLE_MAX_LENGTH = 60
        const val DESCRIPTION_MAX_LENGTH = 200
    }
}

/**
 * Stable snapshot of the edit-form fields passed to [EditDocumentDetailsSheet] and
 * [EditDocumentDetailsDialog]. Grouping them in a data class keeps composable signatures
 * clean and aligns with standard Compose/MVI practice.
 */
data class EditDocumentUiState(
    val title: String = "",
    val description: String = "",
    val isTitleError: Boolean = false,
    val isDescriptionError: Boolean = false,
    val canConfirm: Boolean = true,
)

// ---------------------------------------------------------------------------
// Actions (UI → ViewModel)
// ---------------------------------------------------------------------------

sealed interface DocumentSheetAction {
    /** Open the sheet on the Actions page for the given document id. */
    data class Open(val documentId: String) : DocumentSheetAction

    /** Navigate forward inside the sheet. */
    data object NavigateToEdit : DocumentSheetAction
    data object NavigateToDelete : DocumentSheetAction

    /** Navigate back one page (or close if at root). */
    data object Back : DocumentSheetAction

    /** Close the sheet entirely. */
    data object Dismiss : DocumentSheetAction

    // Edit-form
    data class UpdateTitle(val value: String) : DocumentSheetAction
    data class UpdateDescription(val value: String) : DocumentSheetAction
    data object ConfirmEdit : DocumentSheetAction

    // Document operations delegated back to HomeViewModel via the shared
    // document id — the sheet VM just signals intent.
    data object ConfirmDelete : DocumentSheetAction
}

// ---------------------------------------------------------------------------
// Effects (ViewModel → UI, one-shot)
// ---------------------------------------------------------------------------

sealed interface DocumentSheetEffect {
    /** Request the parent to share the document with the given id. */
    data class RequestShare(val documentId: String) : DocumentSheetEffect
    /** Request the parent to save/export the document with the given id. */
    data class RequestSave(val documentId: String) : DocumentSheetEffect
    /** Request the parent to delete the document with the given id. */
    data class RequestDelete(val documentId: String) : DocumentSheetEffect
}

