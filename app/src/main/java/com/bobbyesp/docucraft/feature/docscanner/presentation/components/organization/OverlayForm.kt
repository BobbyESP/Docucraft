/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.components.organization

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.navigation.overlay.LocalOverlayContext
import com.bobbyesp.docucraft.core.presentation.navigation.overlay.OverlayPresentation
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.sheet.DocumentActionSheetSkeleton
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.folders.NameError

/**
 * An overlay that asks for something and is confirmed or dismissed: a form, or a confirmation. It
 * is a sheet or a dialog as the container it landed in says ([LocalOverlayContext]), with the same
 * heading, body and two buttons in both, so a destination writes its body once.
 *
 * @param confirmText What confirming does, in a word. `null` for an overlay with nothing to
 *   confirm, which only closes.
 * @param destructive Whether confirming deletes something, which the confirm button then says in
 *   the error color.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OverlayForm(
    title: String,
    icon: ImageVector,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    confirmText: String? = null,
    onConfirm: () -> Unit = {},
    confirmEnabled: Boolean = true,
    dismissText: String = stringResource(R.string.cancel),
    destructive: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    when (LocalOverlayContext.current.presentation) {
        OverlayPresentation.Sheet ->
            DocumentActionSheetSkeleton(
                modifier = modifier,
                headingTitle = title,
                headingDescription = description,
                icon = icon,
                iconTint = if (destructive) colorScheme.error else colorScheme.primary,
                content = { Column(modifier = Modifier.padding(16.dp)) { content() } },
                footer = {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.weight(1f).heightIn(min = FooterButtonHeight),
                        ) {
                            Text(text = dismissText)
                        }
                        if (confirmText != null) {
                            Button(
                                onClick = onConfirm,
                                shapes = ButtonDefaults.shapes(),
                                enabled = confirmEnabled,
                                colors =
                                    if (destructive) {
                                        ButtonDefaults.buttonColors(
                                            containerColor = colorScheme.error,
                                            contentColor = colorScheme.onError,
                                        )
                                    } else {
                                        ButtonDefaults.buttonColors()
                                    },
                                modifier = Modifier.weight(1f).heightIn(min = FooterButtonHeight),
                            ) {
                                Text(text = confirmText)
                            }
                        }
                    }
                },
            )

        OverlayPresentation.Dialog ->
            AlertDialog(
                modifier = modifier.widthIn(max = DialogMaxWidth),
                onDismissRequest = onDismiss,
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (destructive) colorScheme.error else colorScheme.primary,
                    )
                },
                title = { Text(text = title, fontWeight = FontWeight.SemiBold) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        if (description != null) {
                            Text(
                                text = description,
                                modifier = Modifier.padding(bottom = 16.dp),
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                        content()
                    }
                },
                confirmButton = {
                    if (confirmText != null) {
                        TextButton(
                            onClick = onConfirm,
                            shapes = ButtonDefaults.shapes(),
                            enabled = confirmEnabled,
                            colors =
                                if (destructive) {
                                    ButtonDefaults.textButtonColors(
                                        contentColor = colorScheme.error
                                    )
                                } else {
                                    ButtonDefaults.textButtonColors()
                                },
                        ) {
                            Text(text = confirmText)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                        Text(text = dismissText)
                    }
                },
            )
    }
}

private val FooterButtonHeight = 48.dp
private val DialogMaxWidth = 560.dp

/**
 * The field a folder or a tag is named in. What is wrong with the name is said under it, in place:
 * it is something the user typed, not a failure.
 *
 * @param takenMessage What to say when another one has the name, which differs by what is named.
 */
@Composable
fun NameField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: NameError?,
    takenMessage: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= NameMaxLength) onValueChange(it) },
        modifier = modifier.fillMaxWidth(),
        label = { Text(text = label) },
        singleLine = true,
        isError = error != null,
        supportingText =
            error?.let {
                {
                    Text(
                        text =
                            when (it) {
                                NameError.Empty -> stringResource(R.string.name_empty)
                                NameError.Taken -> takenMessage
                            }
                    )
                }
            },
        shape = MaterialTheme.shapes.large,
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
    )
}

private const val NameMaxLength = 40
