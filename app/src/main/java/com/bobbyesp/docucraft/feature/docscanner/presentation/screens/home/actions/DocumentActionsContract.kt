/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.actions

import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument

/** What can be asked of the document an overlay is acting on. */
sealed interface DocumentActionsIntent {
    data object Share : DocumentActionsIntent

    data object Export : DocumentActionsIntent

    data object ConfirmDelete : DocumentActionsIntent

    data class ConfirmEdit(val title: String, val description: String) : DocumentActionsIntent
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
    val document: ScannedDocument? = null
)
