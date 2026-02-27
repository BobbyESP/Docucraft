package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.sheet

/**
 * Represents the UI state for the document editing sheet.
 *
 * This state class holds all the necessary information to render and manage
 * the user interface when a user is modifying document metadata or properties
 * within the document scanner feature.
 */
data class EditDocumentUiState(
    val title: String = "",
    val description: String = "",
    val isTitleError: Boolean = false,
    val isDescriptionError: Boolean = false,
    val canConfirm: Boolean = true,
)