/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.actions

import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.domain.StringProvider
import com.bobbyesp.docucraft.core.domain.analytics.AnalyticsEvent
import com.bobbyesp.docucraft.core.domain.notifications.NotificationType
import com.bobbyesp.docucraft.core.domain.repository.AnalyticsHelper
import com.bobbyesp.docucraft.core.util.events.UiEvent
import com.bobbyesp.docucraft.core.util.viewModel.BaseViewModel
import com.bobbyesp.docucraft.feature.docscanner.domain.sharing.DocumentExporter
import com.bobbyesp.docucraft.feature.docscanner.domain.sharing.DocumentSharer
import com.bobbyesp.docucraft.feature.docscanner.domain.sharing.ExportOutcome
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.DeleteDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.UpdateDocumentFieldsUseCase

/**
 * Everything that can be done to one document, scoped to the navigation entry acting on it.
 *
 * All of this used to live in `HomeViewModel`, alongside a hand-rolled page stack for the sheet it
 * was shown in. Home had to hold it because the sheet was part of Home's own state; now that acting
 * on a document is a destination, it can own what it does, and Home is left with listing documents.
 *
 * The document is followed rather than read once, for the same reason the viewer follows it: on a
 * wide window the catalogue stays on screen beside the overlay, so it can change underneath.
 */
class DocumentActionsViewModel(
    private val documentUuid: String,
    observeDocument: ObserveDocumentUseCase,
    private val deleteDocumentUseCase: DeleteDocumentUseCase,
    private val updateDocumentFieldsUseCase: UpdateDocumentFieldsUseCase,
    private val documentSharer: DocumentSharer,
    private val documentExporter: DocumentExporter,
    private val stringProvider: StringProvider,
    private val analyticsHelper: AnalyticsHelper,
) :
    BaseViewModel<DocumentActionsIntent, DocumentActionsUiState, DocumentActionsEffect>(
        initialState = DocumentActionsUiState()
    ) {

    /**
     * Whether the document was ever there. `null` means "not read yet" at first and "deleted"
     * afterwards, and the two call for opposite reactions: wait, then leave.
     */
    private var wasLoaded = false

    init {
        launch {
            observeDocument(documentUuid).collect { document ->
                setState { copy(document = document) }

                if (document != null) wasLoaded = true
                else if (wasLoaded) sendEffect(DocumentActionsEffect.CloseAll)
            }
        }
    }

    override fun onHandleIntent(intent: DocumentActionsIntent) {
        when (intent) {
            DocumentActionsIntent.Share -> share()
            DocumentActionsIntent.Export -> export()
            DocumentActionsIntent.ConfirmDelete -> delete()
            is DocumentActionsIntent.ConfirmEdit -> edit(intent.title, intent.description)
        }
    }

    private fun share() {
        val document = currentState.document ?: return

        runCatching {
                documentSharer.share(document.location)
                analyticsHelper.logEvent(
                    AnalyticsEvent(type = AnalyticsEvent.Types.DOCUMENT_SHARED)
                )
            }
            .onFailure {
                sendUiEvent(
                    UiEvent.ShowMessage(
                        stringProvider.get(R.string.issue_sharing_doc),
                        NotificationType.Error,
                    )
                )
            }
    }

    private fun export() = launch {
        val document = currentState.document ?: return@launch

        val outcome =
            documentExporter.export(
                document = document.location,
                suggestedName = document.title ?: document.filename,
            )

        when (outcome) {
            is ExportOutcome.Saved -> {
                analyticsHelper.logEvent(
                    AnalyticsEvent(type = AnalyticsEvent.Types.DOCUMENT_EXPORTED)
                )
                sendUiEvent(
                    UiEvent.ShowMessage(
                        stringProvider.get(
                            R.string.doc_saved_successfully_to,
                            outcome.location.value,
                        ),
                        NotificationType.Success,
                    )
                )
            }

            // Choosing not to save anywhere is an answer, not an error.
            ExportOutcome.Cancelled -> Unit

            is ExportOutcome.Failed ->
                sendUiEvent(
                    UiEvent.ShowMessage(
                        stringProvider.getError(outcome.cause),
                        NotificationType.Error,
                    )
                )
        }
    }

    /**
     * Closing is left to the document disappearing from underneath, which the observer above
     * notices: the same path that closes these overlays when the document is deleted from another
     * window, so there is only one way out to get right.
     */
    private fun delete() = launch {
        val document = currentState.document ?: return@launch

        deleteDocumentUseCase(document)

        analyticsHelper.logEvent(AnalyticsEvent(type = AnalyticsEvent.Types.DOCUMENT_DELETED))

        sendUiEvent(
            UiEvent.ShowMessage(
                stringProvider.get(R.string.doc_deleted_successfully),
                NotificationType.Success,
            )
        )
    }

    private fun edit(title: String, description: String) = launch {
        updateDocumentFieldsUseCase(
            documentUuid,
            title.trim().ifBlank { null },
            description.trim().ifBlank { null },
        )

        sendUiEvent(
            UiEvent.ShowMessage(
                stringProvider.get(R.string.doc_updated_successfully),
                NotificationType.Success,
            )
        )

        sendEffect(DocumentActionsEffect.Close)
    }
}
