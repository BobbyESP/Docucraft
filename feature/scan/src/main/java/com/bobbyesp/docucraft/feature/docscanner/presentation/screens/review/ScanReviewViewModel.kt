/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.review

import com.bobbyesp.docucraft.core.util.viewModel.BaseViewModel
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Folder
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Tag
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.FoldersRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.TagsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.suggestions.DocumentSuggestions
import com.bobbyesp.docucraft.feature.docscanner.domain.suggestions.SuggestDocumentDetailsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.suggestions.SuggestionOutcome
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SetDocumentTextRecognitionUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.TagDocumentByNameUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.UpdateDocumentFieldsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine

sealed interface ScanReviewIntent {
    /** Keep what the form now says as the document's title and description. */
    data class Save(val title: String, val description: String) : ScanReviewIntent

    /** Have the text of the scan recognized, or not. Written at once, as in its actions. */
    data class SetTextRecognition(val enabled: Boolean) : ScanReviewIntent

    /** Put a suggested tag on the document. */
    data class AddSuggestedTag(val name: String) : ScanReviewIntent

    /** Put the document in the suggested folder. */
    data object MoveToSuggestedFolder : ScanReviewIntent
}

sealed interface ScanReviewEffect {
    /** The review is over: the document is as the user left it. */
    data object Close : ScanReviewEffect
}

/** What the review has to say about suggestions, which is nothing at all on most builds. */
sealed interface SuggestionsUiState {
    /** Nothing makes suggestions here, so the review does not mention them. */
    data object Hidden : SuggestionsUiState

    /** The document is being read, and then described. */
    data object Working : SuggestionsUiState

    /** None will come: the scan has no text until its text is recognized, and that is off. */
    data object TextRecognitionOff : SuggestionsUiState

    /** The document was read, and there is nothing to propose. */
    data object Nothing : SuggestionsUiState

    data object Failed : SuggestionsUiState

    /** @property folder The suggested folder, when there is one and it is still there. */
    data class Ready(val suggestions: DocumentSuggestions, val folder: Folder?) : SuggestionsUiState
}

/**
 * @property document The scan under review. `null` until it is read, and once it is gone.
 * @property folder The folder it is in, or `null` in the root.
 */
data class ScanReviewUiState(
    val document: Document.Managed? = null,
    val folder: Folder? = null,
    val tags: List<Tag> = emptyList(),
    val suggestions: SuggestionsUiState = SuggestionsUiState.Hidden,
)

/**
 * The review of a scan that has just been saved: what the user sees right after the scanner closes.
 *
 * The scan is already a document of the library when this starts. Saving it is not this screen's to
 * do, or to undo: a process that dies here, or a user who walks away, leaves a document saved as it
 * came, which is what skipping the review means. What is changed here is changed on that document,
 * through the same operations its actions use.
 */
class ScanReviewViewModel(
    private val documentUuid: String,
    observeDocument: ObserveDocumentUseCase,
    private val folders: FoldersRepository,
    private val tags: TagsRepository,
    private val updateDocumentFields: UpdateDocumentFieldsUseCase,
    private val setTextRecognition: SetDocumentTextRecognitionUseCase,
    private val tagByName: TagDocumentByNameUseCase,
    private val suggestDetails: SuggestDocumentDetailsUseCase,
) :
    BaseViewModel<ScanReviewIntent, ScanReviewUiState, ScanReviewEffect>(
        initialState = ScanReviewUiState()
    ) {

    private var wasLoaded = false
    private var suggesting: Job? = null

    init {
        launch {
            combine(
                    observeDocument(documentUuid),
                    folders.observeFolderOf(documentUuid),
                    tags.observeTagsOf(documentUuid),
                ) { document, folder, tags ->
                    Triple(document as? Document.Managed, folder, tags)
                }
                .collect { (document, folder, tags) ->
                    setState { copy(document = document, folder = folder, tags = tags) }
                    if (document != null) wasLoaded = true
                    // Deleted from somewhere else while it was being reviewed.
                    else if (wasLoaded) sendEffect(ScanReviewEffect.Close)
                }
        }
        suggest()
    }

    override fun onHandleIntent(intent: ScanReviewIntent) {
        when (intent) {
            is ScanReviewIntent.Save -> save(intent.title, intent.description)
            is ScanReviewIntent.SetTextRecognition -> setRecognition(intent.enabled)
            is ScanReviewIntent.AddSuggestedTag -> launch { tagByName(documentUuid, intent.name) }
            ScanReviewIntent.MoveToSuggestedFolder -> moveToSuggestedFolder()
        }
    }

    private fun save(title: String, description: String) = launch {
        updateDocumentFields(
            documentUuid,
            title.trim().ifBlank { null },
            description.trim().ifBlank { null },
        )
        sendEffect(ScanReviewEffect.Close)
    }

    /**
     * What suggestions are made from is the text, so turning recognition on is what makes them
     * possible: they are asked for again.
     */
    private fun setRecognition(enabled: Boolean) = launch {
        if (setTextRecognition(documentUuid, enabled)) suggest()
    }

    private fun moveToSuggestedFolder() = launch {
        val folder = (currentState.suggestions as? SuggestionsUiState.Ready)?.folder
        if (folder != null) folders.moveDocuments(listOf(documentUuid), folder.uuid)
    }

    /** One at a time: a new request replaces the one that was waiting for the text. */
    private fun suggest() {
        if (!suggestDetails.isAvailable) return

        suggesting?.cancel()
        setState { copy(suggestions = SuggestionsUiState.Working) }
        suggesting = launch {
            val shown =
                when (val outcome = suggestDetails(documentUuid)) {
                    SuggestionOutcome.NotAvailable -> SuggestionsUiState.Hidden
                    SuggestionOutcome.TextRecognitionOff -> SuggestionsUiState.TextRecognitionOff
                    SuggestionOutcome.NoText -> SuggestionsUiState.Nothing
                    SuggestionOutcome.Failed -> SuggestionsUiState.Failed
                    is SuggestionOutcome.Suggested ->
                        if (outcome.suggestions.isEmpty) {
                            SuggestionsUiState.Nothing
                        } else {
                            SuggestionsUiState.Ready(
                                suggestions = outcome.suggestions,
                                folder =
                                    outcome.suggestions.folderUuid?.let { folders.getFolder(it) },
                            )
                        }
                }
            setState { copy(suggestions = shown) }
        }
    }
}
