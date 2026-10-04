/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.tags

import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.domain.StringProvider
import com.bobbyesp.docucraft.core.domain.notifications.NotificationType
import com.bobbyesp.docucraft.core.util.events.UiEvent
import com.bobbyesp.docucraft.core.util.viewModel.BaseViewModel
import com.bobbyesp.docucraft.feature.docscanner.domain.model.LabelColor
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Tag
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.TagChange
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.TagsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ArrangeHomeSectionsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SaveTagUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.TagDocumentByNameUseCase
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.folders.NameError
import kotlinx.coroutines.flow.combine

// ---------------- The tags of one document ----------------

sealed interface DocumentTagsIntent {
    /** Put the tag on the document, or take it off. */
    data class Toggle(val tagUuid: String) : DocumentTagsIntent

    /** Put on the document the tag with this name, creating it if there is none. */
    data class AddByName(val name: String) : DocumentTagsIntent
}

/**
 * @property tags Every tag, by name.
 * @property assigned The uuids of those the document carries.
 */
data class DocumentTagsUiState(
    val tags: List<Tag> = emptyList(),
    val assigned: Set<String> = emptySet(),
    val isLoading: Boolean = true,
)

/** Which tags a document carries. Each tap is written at once: there is nothing to confirm. */
class DocumentTagsViewModel(
    private val documentUuid: String,
    private val tags: TagsRepository,
    private val tagByName: TagDocumentByNameUseCase,
) :
    BaseViewModel<DocumentTagsIntent, DocumentTagsUiState, Nothing>(
        initialState = DocumentTagsUiState()
    ) {

    init {
        launch {
            combine(tags.observeTags(), tags.observeTagsOf(documentUuid)) { all, assigned ->
                    DocumentTagsUiState(
                        tags = all,
                        assigned = assigned.mapTo(HashSet()) { it.uuid },
                        isLoading = false,
                    )
                }
                .collect { state -> setState { state } }
        }
    }

    override fun onHandleIntent(intent: DocumentTagsIntent) {
        when (intent) {
            is DocumentTagsIntent.Toggle ->
                launch {
                    if (intent.tagUuid in currentState.assigned) {
                        tags.untag(documentUuid, intent.tagUuid)
                    } else {
                        tags.tag(documentUuid, intent.tagUuid)
                    }
                }
            is DocumentTagsIntent.AddByName -> launch { tagByName(documentUuid, intent.name) }
        }
    }
}

// ---------------- Every tag ----------------

sealed interface TagsIntent {
    /** Create the tag, or change it, as the form now says. */
    data class Save(val name: String, val color: LabelColor?) : TagsIntent

    /** The name was typed in again, so what was wrong with the last one no longer applies. */
    data object NameEdited : TagsIntent

    /** Give a tag a section of its own in Home, at the end, or take it away. */
    data class SetShownInHome(val tagUuid: String, val shown: Boolean) : TagsIntent

    /** Move a tag's section one place: up for a negative [by]. */
    data class MoveSection(val tagUuid: String, val by: Int) : TagsIntent

    /** Delete the tag. The documents that carry it lose it, and nothing else. */
    data object ConfirmDelete : TagsIntent
}

sealed interface TagsEffect {
    data object Close : TagsEffect

    /** The tag is gone, so every overlay standing on it must go too. */
    data object CloseAll : TagsEffect
}

/**
 * @property homeSections The tags with a section in Home, in the order Home shows them.
 * @property otherTags The rest, by name.
 * @property tag The tag an overlay is acting on, when it is acting on one.
 */
data class TagsUiState(
    val homeSections: List<Tag> = emptyList(),
    val otherTags: List<Tag> = emptyList(),
    val tag: Tag? = null,
    val isLoading: Boolean = true,
    val nameError: NameError? = null,
) {
    val isEmpty: Boolean
        get() = !isLoading && homeSections.isEmpty() && otherTags.isEmpty()
}

/**
 * The tags as a whole, and one of them when an overlay is acting on it: the screen that lists them,
 * the form that names and colors one, and the confirmation that deletes it each have their own.
 *
 * @param tagUuid The tag acted on, or `null` for the list and for a tag being created.
 */
class TagsViewModel(
    private val tagUuid: String?,
    private val tags: TagsRepository,
    private val saveTag: SaveTagUseCase,
    private val arrangeHomeSections: ArrangeHomeSectionsUseCase,
    private val stringProvider: StringProvider,
) : BaseViewModel<TagsIntent, TagsUiState, TagsEffect>(initialState = TagsUiState()) {

    private var wasLoaded = false

    init {
        launch {
            tags.observeTags().collect { found ->
                val acted = found.firstOrNull { it.uuid == tagUuid }
                setState {
                    copy(
                        homeSections =
                            found.filter { it.homePosition != null }.sortedBy { it.homePosition },
                        otherTags = found.filter { it.homePosition == null },
                        tag = acted,
                        isLoading = false,
                    )
                }
                if (tagUuid != null) {
                    if (acted != null) wasLoaded = true
                    else if (wasLoaded) sendEffect(TagsEffect.CloseAll)
                }
            }
        }
    }

    override fun onHandleIntent(intent: TagsIntent) {
        when (intent) {
            is TagsIntent.Save -> save(intent)
            TagsIntent.NameEdited -> setState { copy(nameError = null) }
            is TagsIntent.SetShownInHome ->
                launch {
                    arrangeHomeSections.setShown(intent.tagUuid, intent.shown)
                }
            is TagsIntent.MoveSection ->
                launch {
                    arrangeHomeSections.move(intent.tagUuid, intent.by)
                }
            TagsIntent.ConfirmDelete -> delete()
        }
    }

    private fun save(intent: TagsIntent.Save) = launch {
        when (saveTag(tagUuid, intent.name, intent.color)) {
            TagChange.Done -> sendEffect(TagsEffect.Close)
            TagChange.NameEmpty -> setState { copy(nameError = NameError.Empty) }
            TagChange.NameTaken -> setState { copy(nameError = NameError.Taken) }
            TagChange.NotFound -> sendEffect(TagsEffect.Close)
        }
    }

    /** Closing is left to the tag disappearing, which the observer above notices. */
    private fun delete() = launch {
        val tag = currentState.tag ?: return@launch
        tags.delete(tag.uuid)
        sendUiEvent(
            UiEvent.ShowMessage(
                stringProvider.get(R.string.tag_deleted),
                NotificationType.Success,
            )
        )
    }
}
