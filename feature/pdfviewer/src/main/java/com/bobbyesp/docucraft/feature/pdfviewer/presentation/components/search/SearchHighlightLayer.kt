/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.search

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.search.DocumentSearch
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.search.SearchHighlights
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.selection.PageTextState
import com.bobbyesp.documentcontent.NormalizedRect
import com.composepdf.PdfOverlayScope
import com.composepdf.PdfPageDrawScope

/**
 * The matches of a search, lit on the pages.
 *
 * Every match is tinted; the one the reader is on is stronger, underlined, and, when it comes into
 * view, rings out once from the text, so the eye is taken to it rather than left to look for it
 * among the others. Drawn in the engine's overlay, in the draw phase: panning and zooming never
 * recompose it, and the ring animates without recomposing either.
 *
 * Where the boxes are is worked out when the matches or the pages' text change
 * ([SearchHighlights]), which is only after the reader has stopped moving.
 *
 * @param pages The text of the pages near what is on screen: a match is lit once its page's
 *   geometry is known.
 * @param nightMode Whether the pages are drawn inverted. The colors that read on a light page
 *   vanish on a dark one, as they do for the selection.
 */
@Composable
fun PdfOverlayScope.SearchHighlightLayer(
    search: DocumentSearch?,
    pages: Map<Int, PageTextState>,
    nightMode: Boolean,
) {
    val lit =
        remember(search?.matches, search?.active, pages) { SearchHighlights.of(search, pages) }
    if (lit.isEmpty()) return

    val look = highlightLook(nightMode)
    val ring = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()
    val active = search?.active
    // Once for each match the reader arrives on; stepping on restarts it.
    LaunchedEffect(active) {
        if (active == null) return@LaunchedEffect
        ring.snapTo(1f)
        ring.animateTo(0f, spec)
    }

    DrawOnPages {
        val page = lit[pageIndex] ?: return@DrawOnPages
        val grow = Grow.toPx()
        val corner = CornerRadius(Corner.toPx())

        for (box in page.others) {
            val area = viewerRect(box).inflate(grow)
            drawRoundRect(look.match, area.topLeft, area.size, corner)
        }
        for (box in page.current) {
            val area = viewerRect(box).inflate(grow)
            drawRoundRect(look.current, area.topLeft, area.size, corner)
            // The underline: the one mark that stays legible if the colors are close.
            val y = area.bottom - Underline.toPx() / 2f
            drawLine(
                color = look.edge,
                start = Offset(area.left + corner.x, y),
                end = Offset(area.right - corner.x, y),
                strokeWidth = Underline.toPx(),
                cap = StrokeCap.Round,
            )
            val rippling = ring.value
            if (rippling > 0f) {
                val spread = (1f - rippling) * Ripple.toPx()
                val outer = area.inflate(spread)
                drawRoundRect(
                    color = look.edge.copy(alpha = look.edge.alpha * rippling * RippleAlpha),
                    topLeft = outer.topLeft,
                    size = outer.size,
                    cornerRadius = CornerRadius(corner.x + spread),
                    style = Stroke(width = RippleStroke.toPx()),
                )
            }
        }
    }
}

/** The colors a match is drawn in. */
@Immutable private class HighlightLook(val match: Color, val current: Color, val edge: Color)

/**
 * Colors from the theme, not a fixed yellow: the selection's own color is for text a reader chose;
 * a match is something the app found, so it takes the tertiary accent, and the match the reader is
 * on takes the primary one.
 */
@Composable
private fun highlightLook(nightMode: Boolean): HighlightLook {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme.tertiary, scheme.primary, scheme.inversePrimary, nightMode) {
        if (nightMode) {
            // Inverted pages are dark with light text: a light tint over it, as for the selection.
            HighlightLook(
                match = scheme.inversePrimary.copy(alpha = NightMatchAlpha),
                current = scheme.inversePrimary.copy(alpha = NightCurrentAlpha),
                edge = scheme.inversePrimary,
            )
        } else {
            HighlightLook(
                match = scheme.tertiary.copy(alpha = MatchAlpha),
                current = scheme.primary.copy(alpha = CurrentAlpha),
                edge = scheme.primary,
            )
        }
    }
}

private fun PdfPageDrawScope.viewerRect(box: NormalizedRect): Rect =
    toViewer(Rect(box.left, box.top, box.right, box.bottom))

private const val MatchAlpha = 0.38f
private const val CurrentAlpha = 0.55f
private const val NightMatchAlpha = 0.35f
private const val NightCurrentAlpha = 0.70f
private const val RippleAlpha = 0.9f

/** A little more than the glyphs, so the tint breathes around the text instead of cutting it. */
private val Grow = 1.dp
private val Corner = 3.dp
private val Underline = 2.5.dp
private val Ripple = 10.dp
private val RippleStroke = 2.dp
