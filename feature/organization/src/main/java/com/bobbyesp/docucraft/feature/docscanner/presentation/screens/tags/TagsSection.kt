/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.tags

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.docucraft.core.presentation.navigation.Navigator
import com.bobbyesp.docucraft.core.presentation.navigation.overlay.OverlaySceneStrategy
import com.bobbyesp.docucraft.feature.docscanner.di.TagRef
import com.bobbyesp.docucraft.feature.docscanner.domain.model.LabelColor
import com.bobbyesp.docucraft.feature.docscanner.navigation.DeleteTag
import com.bobbyesp.docucraft.feature.docscanner.navigation.DocumentTags
import com.bobbyesp.docucraft.feature.docscanner.navigation.ManageTags
import com.bobbyesp.docucraft.feature.docscanner.navigation.TagEditor
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.organization.ShowMessages
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The tags: those of one document, every one of them, and what is done to one. The list is a screen
 * of its own; the rest are overlays, placed by [OverlaySceneStrategy].
 */
fun EntryProviderScope<NavKey>.tagsSection(navigator: Navigator) {
    entry<DocumentTags>(metadata = OverlaySceneStrategy.overlay()) { key ->
        val viewModel: DocumentTagsViewModel = koinViewModel { parametersOf(key.documentUuid) }
        val state by viewModel.state.collectAsStateWithLifecycle()

        // What is being typed, not what the document carries.
        var query by rememberSaveable { mutableStateOf("") }

        DocumentTagsForm(
            state = state,
            query = query,
            onQueryChange = { query = it },
            onToggle = { viewModel.onSendIntent(DocumentTagsIntent.Toggle(it)) },
            onAddByName = {
                viewModel.onSendIntent(DocumentTagsIntent.AddByName(query))
                query = ""
            },
            onDismiss = navigator::goBack,
        )
    }

    entry<ManageTags> { key ->
        val viewModel = tagsViewModel(key, tagUuid = null, navigator)
        val state by viewModel.state.collectAsStateWithLifecycle()

        ManageTagsScreen(
            uiState = state,
            onAction = viewModel::onSendIntent,
            onBack = navigator::goBack,
            onEditTag = { uuid -> navigator.goTo(TagEditor(uuid)) },
            onCreateTag = { navigator.goTo(TagEditor()) },
        )
    }

    entry<TagEditor>(metadata = OverlaySceneStrategy.overlay()) { key ->
        val viewModel = tagsViewModel(key, key.tagUuid, navigator)
        val state by viewModel.state.collectAsStateWithLifecycle()
        if (state.isLoading) return@entry
        val tag = state.tag
        // The tag an existing key names is gone: the overlay is about to be closed.
        if (key.tagUuid != null && tag == null) return@entry

        var name by rememberSaveable { mutableStateOf(tag?.name.orEmpty()) }
        var colorKey by rememberSaveable { mutableStateOf(tag?.color) }

        TagEditorForm(
            isNew = tag == null,
            name = name,
            color = LabelColor.of(colorKey),
            nameError = state.nameError,
            onNameChange = {
                name = it
                viewModel.onSendIntent(TagsIntent.NameEdited)
            },
            onColorChange = { colorKey = it?.key },
            onDelete = { tag?.let { navigator.goTo(DeleteTag(it.uuid)) } },
            onDismiss = navigator::goBack,
            onConfirm = { viewModel.onSendIntent(TagsIntent.Save(name, LabelColor.of(colorKey))) },
        )
    }

    entry<DeleteTag>(metadata = OverlaySceneStrategy.overlay()) { key ->
        val viewModel = tagsViewModel(key, key.tagUuid, navigator)
        val state by viewModel.state.collectAsStateWithLifecycle()
        val tag = state.tag ?: return@entry

        DeleteTagForm(
            tag = tag,
            onDismiss = navigator::goBack,
            onConfirm = { viewModel.onSendIntent(TagsIntent.ConfirmDelete) },
        )
    }
}

@Composable
private fun tagsViewModel(entry: NavKey, tagUuid: String?, navigator: Navigator): TagsViewModel {
    val viewModel: TagsViewModel = koinViewModel { parametersOf(TagRef(tagUuid)) }
    val currentNavigator by rememberUpdatedState(navigator)

    ShowMessages(viewModel.defaultEvents)
    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                TagsEffect.Close -> currentNavigator.removeDestination(entry)

                // The tag is gone: the confirmation goes, and the form it was opened from.
                TagsEffect.CloseAll ->
                    currentNavigator.goBackWhile { it is TagEditor || it is DeleteTag }
            }
        }
    }

    return viewModel
}
