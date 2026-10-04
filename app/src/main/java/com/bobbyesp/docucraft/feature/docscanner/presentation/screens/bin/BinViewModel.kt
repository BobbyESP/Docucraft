/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.bin

import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.domain.StringProvider
import com.bobbyesp.docucraft.core.domain.notifications.NotificationType
import com.bobbyesp.docucraft.core.util.events.UiEvent
import com.bobbyesp.docucraft.core.util.viewModel.BaseViewModel
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.DocumentsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.BinRetention
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.DeleteFromBinUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.EmptyBinUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.RestoreDocumentUseCase

sealed interface BinIntent {
    /** Bring the document an overlay is acting on back to the library. */
    data object Restore : BinIntent

    /** Delete the document an overlay is acting on, for good. */
    data object ConfirmDeleteForever : BinIntent

    /** Delete everything in the bin, for good. */
    data object ConfirmEmpty : BinIntent
}

sealed interface BinEffect {
    /** What was asked is done and this overlay has nothing left to show. */
    data object Close : BinEffect

    /** The document left the bin, so every overlay standing on it must go. */
    data object CloseAll : BinEffect
}

/** A document of the bin, and the days it still has there. */
data class BinnedDocument(val document: Document.Managed, val daysLeft: Int)

/**
 * @property documents What is in the bin, the last one deleted first.
 * @property document The one an overlay is acting on, when it is acting on one.
 */
data class BinUiState(
    val documents: List<BinnedDocument> = emptyList(),
    val document: BinnedDocument? = null,
    val isLoading: Boolean = true,
) {
    val isEmpty: Boolean
        get() = !isLoading && documents.isEmpty()
}

/**
 * The bin, and one document of it when an overlay is acting on it: the screen that lists it, the
 * sheet of a document, and the two confirmations each have their own.
 *
 * @param documentUuid The document acted on, or `null` for the bin as a whole.
 * @param now The clock, in epoch milliseconds: what the days a document has left are counted from.
 */
class BinViewModel(
    private val documentUuid: String?,
    private val documents: DocumentsRepository,
    private val restoreDocument: RestoreDocumentUseCase,
    private val deleteFromBin: DeleteFromBinUseCase,
    private val emptyBin: EmptyBinUseCase,
    private val stringProvider: StringProvider,
    private val now: () -> Long = System::currentTimeMillis,
) : BaseViewModel<BinIntent, BinUiState, BinEffect>(initialState = BinUiState()) {

    /** As everywhere a thing is followed: not there yet at first, gone afterwards. */
    private var wasLoaded = false

    init {
        launch {
            documents.observeBin().collect { binned ->
                val at = now()
                val listed = binned.map { document ->
                    BinnedDocument(
                        document = document,
                        daysLeft = BinRetention.daysLeft(document.trashedAtEpochMillis ?: at, at),
                    )
                }
                val acted = listed.firstOrNull { it.document.uuid == documentUuid }
                setState { copy(documents = listed, document = acted, isLoading = false) }

                if (documentUuid != null) {
                    if (acted != null) wasLoaded = true
                    else if (wasLoaded) sendEffect(BinEffect.CloseAll)
                }
            }
        }
    }

    override fun onHandleIntent(intent: BinIntent) {
        when (intent) {
            BinIntent.Restore -> restore()
            BinIntent.ConfirmDeleteForever -> deleteForever()
            BinIntent.ConfirmEmpty -> empty()
        }
    }

    /** Closing is left to the document leaving the bin, which the observer above notices. */
    private fun restore() = launch {
        val binned = currentState.document ?: return@launch
        if (restoreDocument(binned.document.uuid)) say(R.string.bin_restored)
    }

    private fun deleteForever() = launch {
        val binned = currentState.document ?: return@launch
        if (deleteFromBin(binned.document)) say(R.string.bin_deleted_forever)
    }

    private fun empty() = launch {
        emptyBin()
        say(R.string.bin_emptied)
        sendEffect(BinEffect.Close)
    }

    private fun say(message: Int) {
        sendUiEvent(UiEvent.ShowMessage(stringProvider.get(message), NotificationType.Success))
    }
}
