/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.internal.engine

import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.pow

/**
 * Discretizes the continuous zoom range into geometric steps of sqrt(2).
 *
 * Tiles and base pages are always rasterized at one of these steps instead of the exact gesture
 * zoom. This keeps the caches reusable while the user pinches, and because steps are chosen with
 * `ceil`, rendered content is always at a scale >= the displayed zoom, so it is downscaled on
 * screen (sharp) rather than upscaled (blurry). The worst-case overdraw per axis is sqrt(2).
 */
internal object ZoomSteps {
    private val LN_SQRT2 = ln(2.0) / 2.0
    private const val EPS = 1e-4

    const val MIN_LEVEL = -8
    const val MAX_LEVEL = 16

    /** Smallest level whose scale is >= [zoom]. Level 0 corresponds to scale 1.0. */
    fun levelFor(zoom: Float): Int {
        if (zoom <= 0f) return MIN_LEVEL
        val raw = ceil(ln(zoom.toDouble()) / LN_SQRT2 - EPS).toInt()
        return raw.coerceIn(MIN_LEVEL, MAX_LEVEL)
    }

    /** Scale factor of [level]: sqrt(2)^level. */
    fun scaleFor(level: Int): Float = 2.0.pow(level / 2.0).toFloat()
}
