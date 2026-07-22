/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Visual appearance of the viewer. Create instances through [PdfViewerDefaults.style] so
 * unspecified values pick up the current Material theme.
 *
 * @property containerColor Background behind and around the pages. [Color.Unspecified] leaves the
 *   host's background visible.
 * @property pageColor Paper color drawn under each page (visible through transparent PDFs and while
 *   a page renders). Automatically inverted in [nightMode].
 * @property pageCornerRadius Corner radius applied to each page. `0.dp` keeps pages square.
 * @property pageShadowColor Soft drop shadow under each page. [Color.Transparent] disables it.
 * @property nightMode Invert page colors for comfortable reading in the dark. The viewer chrome
 *   ([containerColor], indicators) is not affected.
 * @property scrollIndicator Thin auto-hiding scrollbar shown while scrolling, or `null` to disable
 *   it.
 * @property showPageLoadingIndicator Show a small indicator centered on pages that have not
 *   rendered yet.
 */
@Immutable
data class PdfViewerStyle(
    val containerColor: Color = Color.Unspecified,
    val pageColor: Color = Color.White,
    val pageCornerRadius: Dp = 0.dp,
    val pageShadowColor: Color = Color(0x33000000),
    val nightMode: Boolean = false,
    val scrollIndicator: PdfScrollIndicatorStyle? = PdfScrollIndicatorStyle(),
    val showPageLoadingIndicator: Boolean = true,
)

/**
 * Appearance of the auto-hiding scroll indicator.
 *
 * @property color Thumb color.
 * @property thickness Thumb thickness.
 * @property padding Distance from the viewport edge.
 * @property minLength Lower bound of the thumb length, so it stays grabbable in huge documents.
 */
@Immutable
data class PdfScrollIndicatorStyle(
    val color: Color = Color(0x80808080),
    val thickness: Dp = 4.dp,
    val padding: Dp = 4.dp,
    val minLength: Dp = 48.dp,
)
