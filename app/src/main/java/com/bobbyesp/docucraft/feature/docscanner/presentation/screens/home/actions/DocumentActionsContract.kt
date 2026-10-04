/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.actions

import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document

/** What can be asked of the document an overlay is acting on. */
sealed interface DocumentActionsIntent {
    data object Share : DocumentActionsIntent

    data object Export : DocumentActionsIntent

    data object ConfirmDelete : DocumentActionsIntent

    data class ConfirmEdit(val title: String, val description: String) : DocumentActionsIntent

    /**
     * Have the text of the document's image-only pages recognized, or stop and forget what was
     * recognized.
     */
    data class SetTextRecognition(val enabled: Boolean) : DocumentActionsIntent

    /** Mark the document as a favorite, or take the mark away. */
    data class SetFavorite(val favorite: Boolean) : DocumentActionsIntent

    /** For a document of another app: stop referring to it. Its file is not touched. */
    data object RemoveFromRecents : DocumentActionsIntent
}

/**
 * Where the overlay should go once an action finishes, which is the only navigation it has an
 * opinion about — the shell decides what "closing" means.
 */
sealed interface DocumentActionsEffect {
    /** The action is done and this overlay has nothing left to show. */
    data object Close : DocumentActionsEffect

    /** The document is gone, so every overlay standing on it must go too. */
    data object CloseAll : DocumentActionsEffect
}

data class DocumentActionsUiState(
    /**
     * Null while the document is being read, and again once it is deleted. The overlay closes on
     * the second, which is why deletion needs no separate signal.
     */
    val document: Document.Managed? = null,
    /**
     * The document, when it is one of another app: there is far less to do to it, and none of it is
     * what is done to [document]. At most one of the two is set.
     */
    val linked: Document.Linked? = null,
)
