/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf

import androidx.compose.runtime.Immutable

/**
 * Where a reader is in a document, in a form that outlives the screen it was read on: a page, and a
 * point along it. Pan and zoom mean nothing once the viewport changes shape; this still names the
 * same line.
 *
 * It is what [PdfViewerState.readingPosition] reports and what a viewer can be started at, so a
 * host can keep it for as long as it likes and open the document there again.
 *
 * @property pageIndex Zero-based.
 * @property fraction How far along the page, on the axis the document scrolls: 0 is its leading
 *   edge and 1 its trailing edge.
 */
@Immutable data class PdfReadingPosition(val pageIndex: Int, val fraction: Float)
