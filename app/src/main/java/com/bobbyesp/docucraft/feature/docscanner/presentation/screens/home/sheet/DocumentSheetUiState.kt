package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.sheet

import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument

/**
 * Represents the UI state for the document details sheet in the home screen.
 *
 * This class holds the necessary data to display and manage the state of a specific
 * document when it is being viewed or edited through a bottom sheet or modal interface.
 */
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