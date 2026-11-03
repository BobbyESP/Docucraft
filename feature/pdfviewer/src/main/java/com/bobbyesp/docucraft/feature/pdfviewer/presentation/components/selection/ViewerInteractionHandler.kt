/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.contract.PdfViewerIntent
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.contract.PdfViewerUiState
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.selection.LongPressOutcome
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.selection.SelectionInteraction
import com.bobbyesp.documentcontent.NormalizedPoint
import com.bobbyesp.documentcontent.PageLink
import com.composepdf.PdfInteractionHandler
import com.composepdf.PdfTapEvent
import com.composepdf.PdfViewerState

/**
 * The touches only the viewer can decide: a tap on a link, and a long press on a word.
 *
 * It reads the latest [uiState] when a touch arrives, so one handler lasts as long as its
 * [selectionDrag].
 */
@Composable
internal fun rememberViewerInteractionHandler(
    uiState: PdfViewerUiState,
    pdfState: PdfViewerState,
    selectionDrag: SelectionDrag,
    onIntent: (PdfViewerIntent) -> Unit,
): PdfInteractionHandler {
    val haptics = LocalHapticFeedback.current
    val latest by rememberUpdatedState(uiState)
    val send by rememberUpdatedState(onIntent)
    return remember(pdfState, selectionDrag, haptics) {
        object : PdfInteractionHandler {
            // A tap on a link answers at once (E3), except while text is selected: that tap lets go
            // of the selection, as anywhere else.
            override fun claimsTap(event: PdfTapEvent): Boolean =
                latest.selection == null && latest.pageLinks.linkAt(event) != null

            override fun onTap(event: PdfTapEvent) {
                val (page, link) = latest.pageLinks.linkAt(event) ?: return
                send(PdfViewerIntent.TapLink(page, link, pdfState.pageCount))
            }

            // Decided from the text already read: a long press cannot wait for it.
            override fun onLongPress(event: PdfTapEvent): Boolean {
                val page = event.pageIndex ?: return false
                val at = event.pagePosition ?: return false
                return when (
                    val outcome =
                        SelectionInteraction.longPress(
                            latest.pageText,
                            page,
                            NormalizedPoint(at.x, at.y),
                        )
                ) {
                    is LongPressOutcome.Select -> {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        send(PdfViewerIntent.Select(outcome.selection))
                        selectionDrag.start(
                            anchor = outcome.selection,
                            current = outcome.selection,
                            at = event.position,
                        )
                        true
                    }

                    is LongPressOutcome.NoText -> {
                        haptics.performHapticFeedback(HapticFeedbackType.Reject)
                        send(PdfViewerIntent.NothingToSelect(outcome.reason))
                        false
                    }

                    LongPressOutcome.NoWord,
                    LongPressOutcome.NotReady -> false
                }
            }

            override fun onDrag(event: PdfTapEvent) = selectionDrag.moveTo(event.position)

            override fun onDragEnd() = selectionDrag.end()
        }
    }
}

private fun Map<Int, List<PageLink>>.linkAt(event: PdfTapEvent): Pair<Int, PageLink>? {
    val page = event.pageIndex ?: return null
    val at = event.pagePosition ?: return null
    val point = NormalizedPoint(at.x, at.y)
    val link =
        this[page]?.firstOrNull { link -> link.bounds.any { it.distanceTo(point) <= LinkSlop } }
            ?: return null
    return page to link
}

/** How far off a link a tap still counts as on it, in page units: about a finger's slack. */
private const val LinkSlop = 0.01f
