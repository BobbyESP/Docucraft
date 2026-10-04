/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.navigation.motion

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.blur.BlurRadiusSpec
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import com.bobbyesp.docucraft.core.presentation.navigation.DocucraftNavDisplay
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftBlurDefaults

/**
 * One destination slides out as the next slides in, locked together like a sliding door: full
 * width, same timing, no fade.
 *
 * The full width is not decoration. On a pop `NavDisplay` leaves the outgoing destination on top,
 * and a faded-out one is invisible but still full size and still hit-testable, so it went on
 * swallowing taps meant for the screen underneath. One that travels the whole way cannot.
 *
 * Screens contribute nothing here; a destination that needs to move differently says so in its own
 * `entry` metadata, which `NavDisplay` prefers over this. Those motions, [SharedElementMotion] and
 * [RisingMotion], are defined in this file too.
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

/**
 * Moving between two destinations *through* an element they share, such as Home's search bar
 * growing into the search screen: the element carries the motion, and the destinations around it
 * only cross-fade.
 *
 * The door slide above would fight the element, dragging the whole screen sideways while the bar
 * morphs upward. Both ends use this, so the element's bounds and the fade share one clock: a shared
 * element is only drawn in flight while the destinations are still animating, and one that outlived
 * them would snap to its end.
 */
object SharedElementMotion {

    /** Entry metadata for the destination reached through the shared element. */
    fun metadata(): Map<String, Any> =
        NavDisplay.transitionSpec { crossFade() } +
            NavDisplay.popTransitionSpec { crossFade() } +
            NavDisplay.predictivePopTransitionSpec { crossFade() }

    /** For the element's bounds on both destinations. */
    val bounds: BoundsTransform = BoundsTransform { _, _ -> tween(DURATION, easing = Emphasized) }

    private fun crossFade(): ContentTransform =
        fadeIn(tween(DURATION, easing = Emphasized)) togetherWith
            fadeOut(tween(DURATION, easing = Emphasized))

    private const val DURATION = 350
    private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

/**
 * A destination that rises from the bottom edge over what is on screen, and sinks back to it when
 * left: for something that interrupts what the user was doing rather than following from it, such
 * as the review of a scan as the scanner closes. A step forward in the app slides in from the side;
 * this comes from elsewhere, and says so by where it comes from.
 *
 * What is underneath does not move. It is held where it is while the destination travels over it,
 * and is there, unmoved, when the destination leaves.
 */
object RisingMotion {

    /** Entry metadata for the destination that rises. */
    fun metadata(): Map<String, Any> =
        NavDisplay.transitionSpec { rise() } +
            NavDisplay.popTransitionSpec { sink() } +
            NavDisplay.predictivePopTransitionSpec { sink() }

    private fun rise(): ContentTransform =
        slideInVertically(tween(DURATION, easing = Emphasized)) { height -> height } togetherWith
            hold()

    /**
     * Keeps what is underneath on screen, as it is, for as long as the destination travels: a fade
     * that goes nowhere. With no exit at all it would be gone on the first frame, and the
     * destination would rise over nothing.
     */
    private fun hold(): ExitTransition = fadeOut(tween(DURATION), targetAlpha = 1f)

    // It leaves the whole way, as the door slide does: one that only faded would stay on top,
    // invisible, taking the taps meant for what is under it.
    private fun sink(): ContentTransform =
        EnterTransition.None togetherWith
            slideOutVertically(tween(DURATION, easing = Emphasized)) { height -> height }

    private const val DURATION = 350
    private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

/**
 * The scope shared elements animate in, one per [DocucraftNavDisplay]. Null outside one, as in a
 * preview, where [sharedBoundsAcrossDestinations] simply does nothing.
 */
val LocalNavSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/**
 * Whether this destination has finished arriving. For work that would fight the transition if it
 * ran during it, such as raising the keyboard while a shared element is still in flight. Always
 * true outside a [DocucraftNavDisplay], where nothing arrives.
 */
@Composable
fun isDestinationSettled(): Boolean {
    if (LocalNavSharedTransitionScope.current == null) return true

    return !LocalNavAnimatedContentScope.current.transition.isRunning
}

/**
 * Marks this as the same element as the one under [key] on another destination, so moving between
 * them morphs one into the other with [SharedElementMotion].
 *
 * Bounds rather than a shared element: the two ends look different (a button on one side, a text
 * field on the other), so each keeps its own content and only the container travels, clipped to
 * [shape] throughout.
 */
@Composable
fun Modifier.sharedBoundsAcrossDestinations(key: Any, shape: Shape): Modifier {
    val sharedTransitionScope = LocalNavSharedTransitionScope.current ?: return this
    val animatedVisibilityScope = LocalNavAnimatedContentScope.current

    return with(sharedTransitionScope) {
        this@sharedBoundsAcrossDestinations.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animatedVisibilityScope,
            boundsTransform = SharedElementMotion.bounds,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            clipInOverlayDuringTransition = OverlayClip(shape),
        )
    }
}

/**
 * Takes what is behind a sheet or a dialog out of focus while it is open: it blurs as the overlay
 * arrives and sharpens as it leaves, together with the scrim its container dims it with.
 *
 * A blur of this window's content, not a frosted overlay: the overlay is a window of its own, which
 * nothing drawn here reaches, and it stays a solid Material surface. Below Android 12 it does
 * nothing, and the scrim alone sets the overlay apart, as it always did.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Modifier.outOfFocusBehindOverlay(overlayShowing: Boolean): Modifier {
    // An effect, not a movement: the scheme's effects spec, which never overshoots. A radius that
    // bounced would sharpen the screen again for a moment.
    val blurRadius =
        animateDpAsState(
            targetValue = if (overlayShowing) DocucraftBlurDefaults.BehindOverlayRadius else 0.dp,
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            label = "OutOfFocusBehindOverlay",
        )

    // Only while there is a blur to draw: it puts everything under it in a layer of its own, which
    // nothing needs otherwise. Read in the blur's own block, the radius animates without
    // recomposing.
    val blurring by remember { derivedStateOf { blurRadius.value > 0.dp } }
    return if (blurring) blur { radius = BlurRadiusSpec.uniform(blurRadius.value) } else this
}
