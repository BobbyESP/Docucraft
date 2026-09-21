/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.navigation.motion

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntOffset

/**
 * One destination slides out as the next slides in, locked together like a sliding door: full
 * width, same timing, no fade.
 *
 * The full width is not decoration. On a pop `NavDisplay` leaves the outgoing destination on top,
 * and a faded-out one is invisible but still full size and still hit-testable, so it went on
 * swallowing taps meant for the screen underneath. One that travels the whole way cannot.
 *
 * Screens contribute nothing here; a destination that needs to move differently says so in its own
 * `entry` metadata, which `NavDisplay` prefers over this.
 */
@Immutable
class NavigationMotion internal constructor() {

    /** The new destination arrives from the right, the current one leaves to the left. */
    fun forward(): ContentTransform = slide(arrivingFromTheRight = true)

    /** Coming back: the same door, run the other way. */
    fun backward(): ContentTransform = slide(arrivingFromTheRight = false)

    /**
     * The same motion as [backward], because it is the same journey. Mirroring it on the swipe edge
     * makes a right-edge swipe play [forward] exactly; the platform's own default ignores the edge
     * too.
     */
    fun predictiveBack(): ContentTransform = backward()

    private fun slide(arrivingFromTheRight: Boolean): ContentTransform {
        val arrivesFrom = if (arrivingFromTheRight) 1 else -1
        val leavesTowards = -arrivesFrom

        return slideInHorizontally(TRAVEL) { width -> arrivesFrom * width }
            .togetherWith(slideOutHorizontally(TRAVEL) { width -> leavesTowards * width })
    }

    private companion object {

        /**
         * A duration, not the theme's spring: `MotionScheme.expressive()` bounces and its tail runs
         * long after the movement looks over, while `NavDisplay` holds both destinations composed
         * until it settles. Only a spec that *ends* fixes that. Material's emphasized easing, as
         * elsewhere in the app.
         */
        val TRAVEL: FiniteAnimationSpec<IntOffset> =
            tween(durationMillis = 250, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
    }
}

@Composable fun rememberNavigationMotion(): NavigationMotion = remember { NavigationMotion() }
