/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.common

import androidx.compose.ui.unit.dp
import kotlin.math.sign

/** Which way the user is taking some content: on through it, or back towards its start. */
enum class ScrollHeading {
    Forward,
    Backward,
}

/**
 * Tells a scroll that is going somewhere from one that is not. What hides or shrinks while the user
 * reads on and comes back when they turn around must not follow the first pixel: a finger that
 * settles, or the small correction after a fling, moves the content the other way without the user
 * having changed their mind.
 *
 * The content has a heading only once it has travelled [thresholdPx] the same way.
 */
class ScrollHeadingTracker(private val thresholdPx: Float) {

    /** Distance travelled one way since the last reversal. */
    private var travel = 0f

    /** Forgets the travel so far: the next heading takes the whole threshold again. */
    fun reset() {
        travel = 0f
    }

    /**
     * [delta] is what the content moved, negative while reading forward. Returns where the scroll
     * is heading, or `null` while it has not gone far enough to say.
     */
    fun onScrolled(delta: Float): ScrollHeading? {
        if (delta == 0f) return null
        if (sign(delta) != sign(travel)) travel = 0f
        travel += delta
        return when {
            travel <= -thresholdPx -> ScrollHeading.Forward
            travel >= thresholdPx -> ScrollHeading.Backward
            else -> null
        }
    }
}

/**
 * How far content must travel one way before what floats over it follows. The same everywhere, so
 * the bars of the viewer and the buttons over a list answer to the same gesture.
 */
val ScrollHeadingThreshold = 56.dp
