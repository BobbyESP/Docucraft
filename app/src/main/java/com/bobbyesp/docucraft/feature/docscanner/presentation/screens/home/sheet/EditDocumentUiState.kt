/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.sheet

/**
 * Represents the UI state for the document editing sheet.
 *
 * This state class holds all the necessary information to render and manage the user interface when
 * a user is modifying document metadata or properties within the document scanner feature.
 */
data class EditDocumentUiState(
    val title: String = "",
    val description: String = "",
    val isTitleError: Boolean = false,
    val isDescriptionError: Boolean = false,
    val canConfirm: Boolean = true,
) {
    companion object {
        const val TITLE_MAX_LENGTH = 60
        const val DESCRIPTION_MAX_LENGTH = 200

        /** Derives the whole state from what has been typed, so the limits live in one place. */
        fun of(title: String, description: String): EditDocumentUiState {
            val titleTooLong = title.length > TITLE_MAX_LENGTH
            val descriptionTooLong = description.length > DESCRIPTION_MAX_LENGTH

            return EditDocumentUiState(
                title = title,
                description = description,
                isTitleError = titleTooLong,
                isDescriptionError = descriptionTooLong,
                canConfirm = !titleTooLong && !descriptionTooLong,
            )
        }
    }
}
