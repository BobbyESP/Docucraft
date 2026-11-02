/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.core.designsystem.theme.DocucraftTheme
import com.bobbyesp.docucraft.feature.pdfviewer.R
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * The page number, shown for a moment when the page changes while the bars are hidden: hiding the
 * bars must not hide where the reader is.
 */
@Composable
fun PageIndicatorPill(
    currentPage: Int,
    pageCount: Int,
    barsVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    var showing by remember { mutableStateOf(false) }
    val motion = MaterialTheme.motionScheme

    LaunchedEffect(currentPage, barsVisible) {
        if (barsVisible || pageCount == 0) {
            showing = false
            return@LaunchedEffect
        }
        showing = true
        delay(ShowFor)
        showing = false
    }

    AnimatedVisibility(
        visible = showing,
        enter = fadeIn(motion.defaultEffectsSpec()),
        exit = fadeOut(motion.defaultEffectsSpec()),
        modifier = modifier,
    ) {
        PillLabel(text = stringResource(R.string.page_indicator, currentPage + 1, pageCount))
    }
}

@Composable
private fun PillLabel(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

private val ShowFor = 1_200.milliseconds

@PreviewLightDark
@Composable
private fun PageIndicatorPillPreview() {
    DocucraftTheme { PillLabel(text = "3 / 12") }
}
