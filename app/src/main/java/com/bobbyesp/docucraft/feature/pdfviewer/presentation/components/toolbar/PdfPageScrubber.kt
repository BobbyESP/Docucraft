/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.toolbar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.R
import kotlin.math.roundToInt

/**
 * The viewer's only persistent bottom control: where you are in the document, and a way to get
 * somewhere else.
 *
 * A scrubber rather than page arrows and a number field, because the useful question in a scanned
 * document is "roughly how far in is the page I want", which dragging answers directly and typing a
 * page number does not. Zoom is absent on purpose — pinch, double tap and quick scale already do it
 * better than buttons, and duplicating them here cost the touch targets their size.
 */
@Composable
fun PdfPageScrubber(
    currentPage: Int,
    pageCount: Int,
    onSeekToPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Nothing to seek through in a single-page document.
    if (pageCount <= 1) return

    val lastIndex = (pageCount - 1).coerceAtLeast(1)

    // While dragging, the thumb follows the finger rather than the document, so a slow render
    // cannot make it stutter or snap backwards.
    var dragPosition: Float? by remember { mutableStateOf(null) }
    var lastSeekedPage by remember { mutableIntStateOf(currentPage) }
    val position = dragPosition ?: currentPage.toFloat()
    val displayedPage = (dragPosition?.roundToInt() ?: currentPage) + 1

    val label = stringResource(R.string.page_of, displayedPage.toString(), pageCount.toString())
    val seekDescription = stringResource(R.string.seek_page)

    Surface(
        modifier = modifier.widthIn(max = 420.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Slider(
                value = position.coerceIn(0f, lastIndex.toFloat()),
                onValueChange = { value ->
                    dragPosition = value
                    val target = value.roundToInt().coerceIn(0, lastIndex)
                    // Seek only when the page under the thumb actually changes; a drag emits many
                    // values per page and each seek stops animations and re-plans rendering.
                    if (target != lastSeekedPage) {
                        lastSeekedPage = target
                        onSeekToPage(target)
                    }
                },
                onValueChangeFinished = { dragPosition = null },
                valueRange = 0f..lastIndex.toFloat(),
                // Continuous rather than stepped: a stepped slider draws one tick per page, which
                // is unreadable and wasteful past a few dozen pages. The thumb still lands on a
                // page because the value is rounded above, and it tracks the finger smoothly.
                steps = 0,
                modifier = Modifier.weight(1f).semantics { contentDescription = seekDescription },
            )

            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 1,
            )
        }
    }
}
