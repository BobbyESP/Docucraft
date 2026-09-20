/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.navigation.motion

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntOffset

/**
 * How Docucraft moves between destinations, in one place.
 *
 * Every `NavDisplay` in the app used to carry its own copy of the same three transitions, written
 * as a full-width slide with whatever spring the animation defaults happened to be. This replaces
 * that with the shared axis along X that Material 3 describes for moving through a hierarchy: a
 * short slide — content is related, so it does not travel the whole window — paired with a fade,
 * timed by the theme's [MotionScheme] rather than by numbers written at the call site.
 *
 * Obtained through [rememberNavigationMotion] because `NavDisplay`'s transition lambdas are not
 * composable: the theme has to be read outside them and carried in.
 *
 * Screens contribute nothing to any of this. A destination that genuinely needs to move differently
 * says so in its own `entry` metadata, via `NavDisplay.transitionSpec` and friends, which
 * `NavDisplay` prefers over whatever is configured here.
 */
@Immutable
class NavigationMotion internal constructor(private val scheme: MotionScheme) {

    /** Going somewhere new: it arrives from the leading edge, the current destination gives way. */
    fun forward(): ContentTransform = sharedAxisX(reversed = false)

    /** Coming back: the reverse journey, so the reverse motion. */
    fun backward(): ContentTransform = sharedAxisX(reversed = true)

    /**
     * Coming back by gesture: the same motion as [backward], because it is the same journey.
     *
     * This used to mirror itself on the swipe edge, on the theory that content should follow the
     * finger. It should not. A shared axis describes the *hierarchy* — forward arrives from the
     * leading edge, back leaves towards it — and mirroring that on the gesture makes a right-edge
     * swipe play the forward animation exactly: the destination being left behind slides off to the
     * left while the one being returned to arrives from the right, which is what going somewhere
     * new looks like. Back then looks like back or like forward depending on which side of the
     * screen the user happened to swipe from.
     *
     * The platform agrees: `defaultPredictivePopTransitionSpec` takes the edge and ignores it.
     */
    fun predictiveBack(): ContentTransform = backward()

    private fun sharedAxisX(reversed: Boolean): ContentTransform {
        val enterFrom = if (reversed) -1 else 1
        val exitTowards = -enterFrom

        return (slideInHorizontally(
                animationSpec = scheme.defaultSpatialSpec(),
                initialOffsetX = { width -> enterFrom * (width * TRAVEL).toInt() },
            ) + fadeIn(animationSpec = scheme.defaultEffectsSpec()))
            .togetherWith(
                slideOutHorizontally(
                    animationSpec = scheme.defaultSpatialSpec<IntOffset>(),
                    targetOffsetX = { width -> exitTowards * (width * TRAVEL).toInt() },
                ) + fadeOut(animationSpec = scheme.defaultEffectsSpec())
            )
    }

    private companion object {
        /**
         * How far across the window a destination travels. A shared axis is a nudge, not a journey:
         * sliding the full width reads as two unrelated screens swapping places.
         */
        const val TRAVEL = 0.3f
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberNavigationMotion(): NavigationMotion {
    val scheme = MaterialTheme.motionScheme

    return remember(scheme) { NavigationMotion(scheme) }
}
