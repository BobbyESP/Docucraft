/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.library

import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.domain.notifications.NotificationType
import com.bobbyesp.docucraft.core.domain.usecase.NotifyUserUseCase
import com.bobbyesp.docucraft.core.util.viewModel.BaseViewModel
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SaveLinkedToLibraryUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SaveToLibraryOutcome

sealed interface SaveCopyIntent {
    /** Save it although the library already has a document with the same content. */
    data object Confirm : SaveCopyIntent
}

/** @property isSaving The copy is under way: asking again would start a second one. */
data class SaveCopyUiState(val isSaving: Boolean = false)

sealed interface SaveCopyEffect {
    /** There is nothing left to ask. */
    data object Close : SaveCopyEffect
}

/**
 * The question asked when a document being saved is already in the library: save a second copy, or
 * not. Its own ViewModel because it is its own destination, which can be left and come back to, and
 * the viewer that raised it need not be there when it is answered.
 *
 * What came of it is said through the app's notifications rather than this screen's: by the time
 * there is something to say, this screen is closing.
 */
class SaveCopyToLibraryViewModel(
    private val documentUuid: String,
    private val saveToLibrary: SaveLinkedToLibraryUseCase,
    private val notifyUser: NotifyUserUseCase,
) : BaseViewModel<SaveCopyIntent, SaveCopyUiState, SaveCopyEffect>(SaveCopyUiState()) {

    override fun onHandleIntent(intent: SaveCopyIntent) {
        when (intent) {
            SaveCopyIntent.Confirm -> save()
        }
    }

    private fun save() {
        if (currentState.isSaving) return
        setState { copy(isSaving = true) }

        launch(onError = { finish(saved = false) }) {
            val outcome = saveToLibrary(documentUuid, evenIfAlreadyThere = true)
            finish(saved = outcome == SaveToLibraryOutcome.Saved)
        }
    }

    private fun finish(saved: Boolean) {
        setState { copy(isSaving = false) }
        if (saved) {
            notifyUser(R.string.saved_to_library, type = NotificationType.Success)
        } else {
            notifyUser(R.string.save_to_library_failed, type = NotificationType.Error)
        }
        sendEffect(SaveCopyEffect.Close)
    }
}
