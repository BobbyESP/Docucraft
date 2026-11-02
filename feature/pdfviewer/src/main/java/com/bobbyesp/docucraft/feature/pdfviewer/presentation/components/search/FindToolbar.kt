/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.core.designsystem.theme.DocucraftTheme
import com.bobbyesp.docucraft.feature.pdfviewer.R
import com.bobbyesp.docucraft.feature.pdfviewer.domain.search.MatchRef
import com.bobbyesp.docucraft.feature.pdfviewer.domain.search.SearchMatches
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.TooltipIconButton
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.toolbar.FrostedFloatingToolbar
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.search.DocumentSearch
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.selection.TextUnavailable
import com.bobbyesp.documentcontent.TextSpan
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState

/**
 * Which match the reader is on, out of how many, and the arrows between them: the bottom toolbar
 * with what a search needs in place of what reading needs.
 *
 * The count is useful from the first match: while the document is still being read a ring beside it
 * shows how much has been, and the count only grows.
 *
 * @param search A search with something being looked for.
 * @param onStep Moves to the next match (`true`) or the previous one.
 * @param hazeState Where the pages are recorded.
 */
@Composable
fun FindToolbar(
    search: DocumentSearch,
    onStep: (forward: Boolean) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val canStep = search.total > 0

    FrostedFloatingToolbar(hazeState = hazeState, modifier = modifier) {
        TooltipIconButton(
            icon = Icons.Rounded.KeyboardArrowUp,
            label = stringResource(R.string.search_previous),
            onClick = { onStep(false) },
            enabled = canStep,
            tooltipPosition = TooltipAnchorPosition.Above,
        )
        FindCounter(search)
        TooltipIconButton(
            icon = Icons.Rounded.KeyboardArrowDown,
            label = stringResource(R.string.search_next),
            onClick = { onStep(true) },
            enabled = canStep,
            tooltipPosition = TooltipAnchorPosition.Above,
        )
    }
}

/**
 * What the counter says. [order] is which match it is, so that the number can slide the way the
 * reader is going: up when they move on, down when they go back.
 */
@Immutable private data class CounterLabel(val text: String, val order: Int)

@Composable
private fun counterLabel(search: DocumentSearch): CounterLabel =
    when {
        search.total > 0 ->
            CounterLabel(
                text =
                    stringResource(
                        R.string.search_position,
                        search.activeNumber.coerceAtLeast(1),
                        search.total,
                    ),
                order = search.activeNumber,
            )

        search.isScanning -> CounterLabel(stringResource(R.string.search_searching), order = 0)
        search.unsearchable == TextUnavailable.ImageOnly ->
            CounterLabel(stringResource(R.string.search_unavailable_image), order = 0)

        search.unsearchable == TextUnavailable.UnsupportedDevice ->
            CounterLabel(stringResource(R.string.search_unavailable_unsupported), order = 0)

        else -> CounterLabel(stringResource(R.string.search_no_matches), order = 0)
    }

/** "3 / 12", with a ring beside it while the rest of the document is still being read. */
@Composable
private fun RowScope.FindCounter(search: DocumentSearch) {
    val label = counterLabel(search)
    val motion = MaterialTheme.motionScheme
    val scanning = search.isScanning
    val progress =
        pluralStringResource(
            R.plurals.search_progress,
            search.pageCount,
            search.scannedPages,
            search.pageCount,
        )
    val description =
        if (search.total > 0) {
            stringResource(R.string.search_position_description, search.activeNumber, search.total)
        } else {
            label.text
        }

    Row(
        modifier =
            Modifier.align(Alignment.CenterVertically)
                .widthIn(min = CounterMinWidth, max = CounterMaxWidth)
                .padding(horizontal = 8.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                    // Said when the search settles, not for every page it reads.
                    if (!scanning) liveRegion = LiveRegionMode.Polite
                },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            visible = scanning,
            enter = fadeIn(motion.fastEffectsSpec()) + expandHorizontally(motion.fastSpatialSpec()),
            exit = fadeOut(motion.fastEffectsSpec()) + shrinkHorizontally(motion.fastSpatialSpec()),
        ) {
            CircularProgressIndicator(
                progress = { search.progress },
                modifier =
                    Modifier.padding(end = 8.dp).size(16.dp).semantics {
                        contentDescription = progress
                    },
                color = LocalContentColor.current,
                trackColor = LocalContentColor.current.copy(alpha = TrackAlpha),
                strokeWidth = 2.dp,
            )
        }

        AnimatedContent(
            targetState = label,
            transitionSpec = {
                val forward = targetState.order >= initialState.order
                val enter = slideInVertically(motion.fastSpatialSpec()) { if (forward) it else -it }
                val exit = slideOutVertically(motion.fastSpatialSpec()) { if (forward) -it else it }
                (enter + fadeIn(motion.fastEffectsSpec())) togetherWith
                    (exit + fadeOut(motion.fastEffectsSpec())) using
                    SizeTransform(clip = false)
            },
            contentAlignment = Alignment.Center,
            contentKey = { it.text },
            label = "match",
        ) { shown ->
            Text(
                text = shown.text,
                style =
                    if (search.total > 0) {
                        MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum")
                    } else {
                        MaterialTheme.typography.labelMedium
                    },
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val CounterMinWidth = 88.dp

/** Wide enough for the longest thing it says, which is why the search found nothing. */
private val CounterMaxWidth = 232.dp

private const val TrackAlpha = 0.2f

@PreviewLightDark
@Composable
private fun FindToolbarPreview() {
    DocucraftTheme {
        FindToolbar(
            search =
                DocumentSearch(
                    query = "invoice",
                    matches = SearchMatches.None.with(0, listOf(TextSpan(0, 7), TextSpan(20, 27))),
                    active = MatchRef(0, 1),
                    activeNumber = 2,
                    scannedPages = 4,
                    pageCount = 12,
                ),
            onStep = {},
            hazeState = rememberHazeState(),
        )
    }
}
