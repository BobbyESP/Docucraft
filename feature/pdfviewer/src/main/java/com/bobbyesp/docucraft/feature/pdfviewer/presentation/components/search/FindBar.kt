/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
@file:OptIn(ExperimentalMaterial3Api::class)

package com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.core.designsystem.theme.DocucraftBlurDefaults
import com.bobbyesp.docucraft.core.designsystem.theme.DocucraftTheme
import com.bobbyesp.docucraft.core.designsystem.theme.frosted
import com.bobbyesp.docucraft.feature.pdfviewer.R
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.TooltipIconButton
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState

/**
 * The search field, shown in the top bar's place as a frosted bar with a pill-shaped field. The
 * count and arrows live in [FindToolbar].
 *
 * The keyboard's search key dismisses the keyboard and keeps the matches; a hardware Enter goes to
 * the next match, Shift+Enter to the previous one.
 *
 * @param field The typed text.
 * @param focusRequests The field takes focus when this goes up; see
 *   [ViewerSearchState.focusRequests].
 * @param onClose Leaves the search.
 * @param onStep Moves to the next match (`true`) or the previous one.
 * @param hazeState Where the pages are recorded, for the frosted background.
 */
@Composable
fun FindBar(
    field: TextFieldState,
    focusRequests: Int,
    onClose: () -> Unit,
    onStep: (forward: Boolean) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val hasText by remember(field) { derivedStateOf { field.text.isNotEmpty() } }
    val placeholder = stringResource(R.string.search_in_document)

    LaunchedEffect(focusRequests) { if (focusRequests > 0) focusRequester.requestFocus() }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .frosted(
                    state = hazeState,
                    style =
                        DocucraftBlurDefaults.surfaceStyle(
                            MaterialTheme.colorScheme.surfaceContainer
                        ),
                )
                .windowInsetsPadding(WindowInsets.statusBars),
        contentAlignment = Alignment.Center,
    ) {
        FieldPill(
            field = field,
            hasText = hasText,
            placeholder = placeholder,
            focusRequester = focusRequester,
            onClose = onClose,
            onStep = onStep,
            onDone = { focusManager.clearFocus() },
        )
    }
}

@Composable
private fun FieldPill(
    field: TextFieldState,
    hasText: Boolean,
    placeholder: String,
    focusRequester: FocusRequester,
    onClose: () -> Unit,
    onStep: (forward: Boolean) -> Unit,
    onDone: () -> Unit,
) {
    Surface(
        modifier =
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                .widthIn(max = MaxWidth)
                .fillMaxWidth()
                .height(PillHeight),
        shape = CircleShape,
        // Solid on the frosted bar, so it reads as a field.
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TooltipIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                label = stringResource(R.string.search_close),
                onClick = onClose,
            )

            BasicTextField(
                state = field,
                modifier =
                    Modifier.weight(1f)
                        .padding(horizontal = 4.dp)
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent { event ->
                            val enter = event.type == KeyEventType.KeyDown && event.key == Key.Enter
                            if (enter) onStep(!event.isShiftPressed)
                            enter
                        },
                textStyle =
                    MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Search,
                    ),
                onKeyboardAction = KeyboardActionHandler { onDone() },
                decorator = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (!hasText) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )

            val motion = MaterialTheme.motionScheme
            AnimatedVisibility(
                visible = hasText,
                enter = fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec()),
                exit = fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec()),
            ) {
                TooltipIconButton(
                    icon = Icons.Rounded.Close,
                    label = stringResource(R.string.search_clear),
                    onClick = { field.clearText() },
                )
            }
        }
    }
}

/** With the 8 dp above and below, the bar is as tall as the top bar it replaces. */
private val PillHeight = 48.dp

/** Keeps the field from stretching across a wide window. */
private val MaxWidth = 640.dp

@PreviewLightDark
@Composable
private fun FindBarPreview() {
    DocucraftTheme {
        FindBar(
            field = TextFieldState("invoice"),
            focusRequests = 0,
            onClose = {},
            onStep = {},
            hazeState = rememberHazeState(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
