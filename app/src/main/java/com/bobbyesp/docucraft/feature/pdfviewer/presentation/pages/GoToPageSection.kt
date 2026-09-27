/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.navigation.Navigator
import com.bobbyesp.docucraft.core.presentation.navigation.overlay.LocalOverlayContext
import com.bobbyesp.docucraft.core.presentation.navigation.overlay.OverlayPresentation
import com.bobbyesp.docucraft.core.presentation.navigation.overlay.OverlaySceneStrategy
import com.bobbyesp.docucraft.feature.pdfviewer.navigation.GoToPage
import org.koin.compose.koinInject

/**
 * *Go to page*, for both hosts that show documents. A sheet on narrow windows and a dialog on wide
 * ones, as the overlay strategy decides. It hands the page to [ViewerPageRequests] and leaves; the
 * viewer scrolls there.
 *
 * It replaces a text field inside the bottom toolbar, which raised the keyboard underneath the bar.
 */
fun EntryProviderScope<NavKey>.goToPageSection(navigator: Navigator) {
    entry<GoToPage>(metadata = OverlaySceneStrategy.overlay()) { key ->
        val requests: ViewerPageRequests = koinInject()
        val initial = (key.currentPage + 1).toString()
        // What has been typed, not what the document is: saved, so a rotation keeps it.
        var input by
            rememberSaveable(stateSaver = TextFieldValue.Saver) {
                mutableStateOf(TextFieldValue(initial, selection = TextRange(0, initial.length)))
            }
        val page = input.text.toIntOrNull()?.takeIf { it in 1..key.pageCount }
        val confirm = {
            if (page != null) {
                requests.request(key.document, page - 1)
                navigator.removeDestination(key)
            }
        }

        val field: @Composable (Modifier) -> Unit = { modifier ->
            PageNumberField(
                value = input,
                onValueChange = { value ->
                    input = value.copy(text = value.text.filter(Char::isDigit).take(MaxDigits))
                },
                pageCount = key.pageCount,
                isValid = page != null || input.text.isEmpty(),
                onGo = confirm,
                modifier = modifier,
            )
        }

        when (LocalOverlayContext.current.presentation) {
            OverlayPresentation.Sheet ->
                GoToPageSheet(
                    field = field,
                    canGo = page != null,
                    onGo = confirm,
                    onCancel = navigator::goBack,
                )

            OverlayPresentation.Dialog ->
                AlertDialog(
                    modifier = Modifier.widthIn(max = DialogMaxWidth),
                    onDismissRequest = navigator::goBack,
                    title = { Text(stringResource(R.string.go_to_page)) },
                    text = { field(Modifier.fillMaxWidth()) },
                    confirmButton = {
                        TextButton(onClick = confirm, enabled = page != null) {
                            Text(stringResource(R.string.go))
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
private fun GoToPageSheet(
    field: @Composable (Modifier) -> Unit,
    canGo: Boolean,
    onGo: () -> Unit,
    onCancel: () -> Unit,
) {
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
            text = stringResource(R.string.go_to_page),
            style = MaterialTheme.typography.headlineSmallEmphasized,
        )
        field(Modifier.fillMaxWidth())
        Button(
            onClick = onGo,
            enabled = canGo,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.go))
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

@Composable
private fun PageNumberField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    pageCount: Int,
    isValid: Boolean,
    onGo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    // Straight to the keyboard with the current page selected: typing replaces it.
    LaunchedEffect(Unit) { focus.requestFocus() }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.focusRequester(focus),
        label = { Text(stringResource(R.string.page_number)) },
        supportingText = { Text(stringResource(R.string.page_range, pageCount)) },
        isError = !isValid,
        singleLine = true,
        keyboardOptions =
            KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
    )
}

/** Enough for any page number a PDF will realistically have. */
private const val MaxDigits = 6

private val DialogMaxWidth = 560.dp
