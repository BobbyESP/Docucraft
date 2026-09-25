/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.documentcontent

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A rectangle in normalized page coordinates: `[0, 1] × [0, 1]` over the page as it is displayed
 * (crop box and rotation applied), origin at the top left. Every provider speaks this space, so the
 * viewer can place content without knowing where it came from: page points for the PDF's own text,
 * bitmap pixels for OCR.
 */
data class NormalizedRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float
        get() = right - left

    val height: Float
        get() = bottom - top

    operator fun contains(point: NormalizedPoint): Boolean =
        point.x in left..right && point.y in top..bottom

    /** The smallest rectangle holding both. */
    fun union(other: NormalizedRect): NormalizedRect =
        NormalizedRect(
            left = min(left, other.left),
            top = min(top, other.top),
            right = max(right, other.right),
            bottom = max(bottom, other.bottom),
        )

    /** How far [point] is from this rectangle; `0` inside it. */
    fun distanceTo(point: NormalizedPoint): Float {
        val dx = horizontalDistanceTo(point.x)
        val dy = verticalDistanceTo(point.y)
        return sqrt(dx * dx + dy * dy)
    }

    internal fun horizontalDistanceTo(x: Float): Float = max(0f, max(left - x, x - right))

    internal fun verticalDistanceTo(y: Float): Float = max(0f, max(top - y, y - bottom))
}

/** A point in normalized page coordinates. See [NormalizedRect]. */
data class NormalizedPoint(val x: Float, val y: Float)
