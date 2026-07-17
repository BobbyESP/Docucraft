/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset

/**
 * A tap or long-press on the viewer, resolved against the document layout.
 *
 * @property position Position of the touch in viewer coordinates (pixels).
 * @property pageIndex Index of the page under the touch, or `null` when the touch landed on the
 *   background between/around pages.
 * @property pagePosition Position on the page normalized to `[0, 1] × [0, 1]` (top-left origin), or
 *   `null` when [pageIndex] is `null`. Stable across zoom levels — ideal for mapping taps to PDF
 *   content such as links or annotations.
 */
@Immutable
data class PdfTapEvent(
    val position: Offset,
    val pageIndex: Int?,
    val pagePosition: Offset?,
)
