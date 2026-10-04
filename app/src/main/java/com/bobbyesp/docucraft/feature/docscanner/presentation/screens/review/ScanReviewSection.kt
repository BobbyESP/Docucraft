/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.review

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
import com.bobbyesp.docucraft.core.presentation.navigation.motion.RisingMotion
import com.bobbyesp.docucraft.core.presentation.screens.preferences.navigation.Settings
import com.bobbyesp.docucraft.feature.docscanner.navigation.DocumentTags
import com.bobbyesp.docucraft.feature.docscanner.navigation.MoveToFolder
import com.bobbyesp.docucraft.feature.docscanner.navigation.ReviewScan
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.actions.EditDocumentUiState
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The review of a scan that was just saved: a screen of its own that rises over Home
 * ([RisingMotion]), not a step forward from it. Its folder and its tags are chosen in the overlays
 * the document's actions already use, which open over this screen and come back to it.
 */
fun EntryProviderScope<NavKey>.scanReviewSection(navigator: Navigator) {
    entry<ReviewScan>(metadata = RisingMotion.metadata()) { key ->
        val viewModel: ScanReviewViewModel = koinViewModel { parametersOf(key.documentUuid) }
        val state by viewModel.state.collectAsStateWithLifecycle()
        val currentNavigator by rememberUpdatedState(navigator)

        LaunchedEffect(viewModel) {
            viewModel.effects.collectLatest { effect ->
                when (effect) {
                    // This overlay, not whatever is on top of the stack.
                    ScanReviewEffect.Close -> currentNavigator.removeDestination(key)
                }
            }
        }

        val document = state.document ?: return@entry

        // What is being typed, not what the document is: kept here so that it survives a rotation
        // and process death.
        var title by rememberSaveable { mutableStateOf(document.title.orEmpty()) }
        var description by rememberSaveable { mutableStateOf(document.description.orEmpty()) }

        ScanReviewScreen(
            state = state,
            document = document,
            fields = EditDocumentUiState.of(title, description),
            onTitleChange = { title = it },
            onDescriptionChange = { description = it },
            onChooseFolder = { navigator.goTo(MoveToFolder(documentUuid = key.documentUuid)) },
            onChooseTags = { navigator.goTo(DocumentTags(key.documentUuid)) },
            onTextRecognitionChange = {
                viewModel.onSendIntent(ScanReviewIntent.SetTextRecognition(it))
            },
            onAddSuggestedTag = { viewModel.onSendIntent(ScanReviewIntent.AddSuggestedTag(it)) },
            onMoveToSuggestedFolder = {
                viewModel.onSendIntent(ScanReviewIntent.MoveToSuggestedFolder)
            },
            onOpenSettings = { navigator.goTo(Settings) },
            // Skipping keeps the scan as it was saved, which is what it already is.
            onSkip = navigator::goBack,
            onSave = { viewModel.onSendIntent(ScanReviewIntent.Save(title, description)) },
        )
    }
}
