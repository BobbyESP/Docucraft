/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.lerp

/**
 * Takes a theme from one color scheme to the next by moving every color of it there.
 *
 * Material provides the color scheme through a static composition local, so each scheme it is given
 * recomposes everything under the theme, skipping nothing. A change therefore costs a recomposition
 * for every scheme on the way, and the way is cut into [Steps]: that many recompositions and no
 * more, however fast the display refreshes.
 *
 * What is on screen is the app itself all the way through, so it goes on scrolling and answering
 * while its colors move. The change used to be one fade of a picture of the last frame over the new
 * theme, which cost a single recomposition, and left that picture standing still over whatever
 * moved under it.
 */
@Stable
internal class ThemeTransitionState(initialScheme: ColorScheme) {

    /** The scheme to give Material: the target, or a step on the way to it. */
    var colorScheme: ColorScheme by mutableStateOf(initialScheme)
        private set

    /** Read when an animation starts, never in composition: it recomposes nothing. */
    private var isRunning = false

    /**
     * [base], except that what components animate of their own during a change keeps up with it.
     * The same object for the same [base], so a change recomposes nothing on its account.
     */
    fun motionScheme(base: MotionScheme): MotionScheme =
        motionSchemes?.takeIf { it.base === base }
            ?: ThemeTransitionMotionScheme(base, isChanging = { isRunning }).also {
                motionSchemes = it
            }

    private var motionSchemes: ThemeTransitionMotionScheme? = null

    /**
     * Moves to [target] from whatever is showing, which is a step of the last change when that one
     * was still under way: back-to-back changes continue from the screen.
     */
    internal suspend fun transitionTo(target: ColorScheme) {
        val start = colorScheme
        if (target === start) return
        isRunning = true
        try {
            var shown = 0
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = tween(durationMillis = DurationMillis, easing = LinearEasing),
            ) { fraction, _ ->
                val step = (fraction * Steps).toInt()
                if (step != shown) {
                    shown = step
                    colorScheme =
                        if (step >= Steps) target else lerp(start, target, step / Steps.toFloat())
                }
            }
            colorScheme = target
            // The last scheme is composed after this frame's callbacks: until it has been, the
            // change is not over for what recomposes with it.
            withFrameNanos {}
        } finally {
            isRunning = false
        }
    }

    internal companion object {
        /** Long enough to read as a change of light rather than a cut. */
        const val DurationMillis = 100

        /**
         * How many schemes a change goes through at most, the last being the target: one every 10
         * ms, which the eye takes for a continuous change of color. A display that draws a frame
         * less often than that shows one scheme per frame, and skips the rest.
         */
        const val Steps = 10
    }
}

/** Keeps [target] as the scheme to reach, moving to it each time it changes. */
@Composable
internal fun rememberThemeTransition(target: ColorScheme): ThemeTransitionState {
    val state = remember { ThemeTransitionState(target) }
    LaunchedEffect(state, target) { state.transitionTo(target) }
    return state
}

/**
 * The scheme [fraction] of the way from [start] to [stop], color by color. In Oklab, as
 * [androidx.compose.ui.graphics.lerp] works, so that a light theme goes dark through greys of its
 * own hue rather than through mud.
 */
internal fun lerp(start: ColorScheme, stop: ColorScheme, fraction: Float): ColorScheme =
    ColorScheme(
        primary = lerp(start.primary, stop.primary, fraction),
        onPrimary = lerp(start.onPrimary, stop.onPrimary, fraction),
        primaryContainer = lerp(start.primaryContainer, stop.primaryContainer, fraction),
        onPrimaryContainer = lerp(start.onPrimaryContainer, stop.onPrimaryContainer, fraction),
        inversePrimary = lerp(start.inversePrimary, stop.inversePrimary, fraction),
        secondary = lerp(start.secondary, stop.secondary, fraction),
        onSecondary = lerp(start.onSecondary, stop.onSecondary, fraction),
        secondaryContainer = lerp(start.secondaryContainer, stop.secondaryContainer, fraction),
        onSecondaryContainer =
            lerp(start.onSecondaryContainer, stop.onSecondaryContainer, fraction),
        tertiary = lerp(start.tertiary, stop.tertiary, fraction),
        onTertiary = lerp(start.onTertiary, stop.onTertiary, fraction),
        tertiaryContainer = lerp(start.tertiaryContainer, stop.tertiaryContainer, fraction),
        onTertiaryContainer = lerp(start.onTertiaryContainer, stop.onTertiaryContainer, fraction),
        background = lerp(start.background, stop.background, fraction),
        onBackground = lerp(start.onBackground, stop.onBackground, fraction),
        surface = lerp(start.surface, stop.surface, fraction),
        onSurface = lerp(start.onSurface, stop.onSurface, fraction),
        surfaceVariant = lerp(start.surfaceVariant, stop.surfaceVariant, fraction),
        onSurfaceVariant = lerp(start.onSurfaceVariant, stop.onSurfaceVariant, fraction),
        surfaceTint = lerp(start.surfaceTint, stop.surfaceTint, fraction),
        inverseSurface = lerp(start.inverseSurface, stop.inverseSurface, fraction),
        inverseOnSurface = lerp(start.inverseOnSurface, stop.inverseOnSurface, fraction),
        error = lerp(start.error, stop.error, fraction),
        onError = lerp(start.onError, stop.onError, fraction),
        errorContainer = lerp(start.errorContainer, stop.errorContainer, fraction),
        onErrorContainer = lerp(start.onErrorContainer, stop.onErrorContainer, fraction),
        outline = lerp(start.outline, stop.outline, fraction),
        outlineVariant = lerp(start.outlineVariant, stop.outlineVariant, fraction),
        scrim = lerp(start.scrim, stop.scrim, fraction),
        surfaceBright = lerp(start.surfaceBright, stop.surfaceBright, fraction),
        surfaceDim = lerp(start.surfaceDim, stop.surfaceDim, fraction),
        surfaceContainer = lerp(start.surfaceContainer, stop.surfaceContainer, fraction),
        surfaceContainerHigh =
            lerp(start.surfaceContainerHigh, stop.surfaceContainerHigh, fraction),
        surfaceContainerHighest =
            lerp(start.surfaceContainerHighest, stop.surfaceContainerHighest, fraction),
        surfaceContainerLow = lerp(start.surfaceContainerLow, stop.surfaceContainerLow, fraction),
        surfaceContainerLowest =
            lerp(start.surfaceContainerLowest, stop.surfaceContainerLowest, fraction),
        primaryFixed = lerp(start.primaryFixed, stop.primaryFixed, fraction),
        primaryFixedDim = lerp(start.primaryFixedDim, stop.primaryFixedDim, fraction),
        onPrimaryFixed = lerp(start.onPrimaryFixed, stop.onPrimaryFixed, fraction),
        onPrimaryFixedVariant =
            lerp(start.onPrimaryFixedVariant, stop.onPrimaryFixedVariant, fraction),
        secondaryFixed = lerp(start.secondaryFixed, stop.secondaryFixed, fraction),
        secondaryFixedDim = lerp(start.secondaryFixedDim, stop.secondaryFixedDim, fraction),
        onSecondaryFixed = lerp(start.onSecondaryFixed, stop.onSecondaryFixed, fraction),
        onSecondaryFixedVariant =
            lerp(start.onSecondaryFixedVariant, stop.onSecondaryFixedVariant, fraction),
        tertiaryFixed = lerp(start.tertiaryFixed, stop.tertiaryFixed, fraction),
        tertiaryFixedDim = lerp(start.tertiaryFixedDim, stop.tertiaryFixedDim, fraction),
        onTertiaryFixed = lerp(start.onTertiaryFixed, stop.onTertiaryFixed, fraction),
        onTertiaryFixedVariant =
            lerp(start.onTertiaryFixedVariant, stop.onTertiaryFixedVariant, fraction),
    )

/**
 * [base], whose effects specs are all but immediate whenever [isChanging], so that what Material's
 * components animate of their own keeps up with the theme instead of trailing behind it.
 *
 * A spring rather than a snap, and it matters: a component that animates its colors as a transition
 * has its target moved by every step of a change, and Compose keeps the spec it was given for an
 * animation that is interrupted only when that spec is a spring. Anything else is replaced by a
 * default spring, which left list items a few steps behind the screen they were on.
 *
 * While nothing changes the specs are [base]'s own, the same objects every time. Material remembers
 * what it animates by the spec it is given: a new one on every call made a button forget the shape
 * it was morphing from each time it recomposed, so pressing it jumped to the pressed shape.
 */
@Suppress("UNCHECKED_CAST")
private class ThemeTransitionMotionScheme(val base: MotionScheme, val isChanging: () -> Boolean) :
    MotionScheme by base {

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> =
        if (isChanging()) Immediate as FiniteAnimationSpec<T> else base.defaultEffectsSpec()

    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> =
        if (isChanging()) Immediate as FiniteAnimationSpec<T> else base.fastEffectsSpec()

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> =
        if (isChanging()) Immediate as FiniteAnimationSpec<T> else base.slowEffectsSpec()
}

/**
 * Close behind its target, by some 15 ms, and not there before the next step: a transition that
 * comes to rest takes a couple of frames to start again, and one that rested between steps would
 * spend the whole change starting.
 */
private val Immediate: SpringSpec<Any> = spring(stiffness = 20_000f)
