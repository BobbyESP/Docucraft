/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.navigation.Navigator
import com.bobbyesp.docucraft.core.presentation.navigation.overlay.LocalOverlayContext
import com.bobbyesp.docucraft.core.presentation.navigation.overlay.OverlayPresentation
import com.bobbyesp.docucraft.core.presentation.navigation.overlay.OverlaySceneStrategy
import com.bobbyesp.docucraft.feature.pdfviewer.navigation.SaveCopyToLibrary
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Asked when a document being saved into the library is already there: save a second copy, or leave
 * it. A destination, so that it survives a rotation and Back answers "no"; a sheet or a dialog,
 * whichever the window has room for.
 */
fun EntryProviderScope<NavKey>.saveCopyToLibrarySection(navigator: Navigator) {
    entry<SaveCopyToLibrary>(metadata = OverlaySceneStrategy.overlay()) { key ->
        val viewModel: SaveCopyToLibraryViewModel =
            koinViewModel(key = key.documentUuid) { parametersOf(key.documentUuid) }
        val state by viewModel.state.collectAsStateWithLifecycle()

        val currentNavigator by rememberUpdatedState(navigator)
        LaunchedEffect(viewModel) {
            viewModel.effects.collectLatest { effect ->
                when (effect) {
                    // This destination, not whatever is on top of the stack by then.
                    SaveCopyEffect.Close -> currentNavigator.removeDestination(key)
                }
            }
        }

        val confirm = { viewModel.onSendIntent(SaveCopyIntent.Confirm) }

        when (LocalOverlayContext.current.presentation) {
            OverlayPresentation.Sheet ->
                SaveCopySheet(
                    isSaving = state.isSaving,
                    onConfirm = confirm,
                    onCancel = navigator::goBack,
                )

            OverlayPresentation.Dialog ->
                AlertDialog(
                    modifier = Modifier.widthIn(max = DialogMaxWidth),
                    onDismissRequest = navigator::goBack,
                    title = { Text(stringResource(R.string.already_in_library_title)) },
                    text = { Text(stringResource(R.string.already_in_library_desc)) },
                    confirmButton = {
                        TextButton(onClick = confirm, enabled = !state.isSaving) {
                            Text(stringResource(R.string.save_another_copy))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = navigator::goBack) {
                            Text(stringResource(R.string.cancel))
                        }
                    },
                )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SaveCopySheet(isSaving: Boolean, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(
                    bottom =
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                            24.dp
                ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.already_in_library_title),
            style = MaterialTheme.typography.headlineSmallEmphasized,
        )
        Text(
            text = stringResource(R.string.already_in_library_desc),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onConfirm,
            enabled = !isSaving,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.save_another_copy))
        }
        OutlinedButton(
            onClick = onCancel,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.cancel))
        }
    }
}

private val DialogMaxWidth = 560.dp
