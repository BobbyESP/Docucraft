/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VectorizedFiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize

/**
 * Takes the app from one color scheme to the next with a single crossfade.
 *
 * A new color scheme recomposes the whole tree, so it is applied once: the last frame drawn with
 * the old scheme is kept as an image and fades out on top, read only while drawing. What another
 * window draws, such as a dialog, switches without the fade.
 */
@Stable
internal class ThemeTransitionState(initialScheme: ColorScheme, private val layer: GraphicsLayer) {

    /** The scheme to give Material: the new one is applied only once the old frame is kept. */
    var colorScheme: ColorScheme by mutableStateOf(initialScheme)
        private set

    private var outgoingFrame: ImageBitmap? by mutableStateOf(null)
    private val outgoingAlpha = Animatable(0f)

    /**
     * [base], except that color animations starting during a fade snap, so components do not trail
     * behind it. The same object for the same [base], so a fade recomposes nothing.
     */
    fun motionScheme(base: MotionScheme): MotionScheme =
        motionSchemes?.takeIf { it.base === base }
            ?: ThemeTransitionMotionScheme(base, isFading = { outgoingFrame != null }).also {
                motionSchemes = it
            }

    private var motionSchemes: ThemeTransitionMotionScheme? = null

    internal suspend fun transitionTo(target: ColorScheme, animationSpec: AnimationSpec<Float>) {
        if (target == colorScheme) return
        // Nothing drawn yet, so nothing to fade.
        if (layer.isReleased || layer.size == IntSize.Zero) {
            colorScheme = target
            return
        }
        // Includes any image still fading, so back-to-back changes continue from the screen.
        val lastFrame = layer.toImageBitmap()
        outgoingAlpha.snapTo(1f)
        outgoingFrame = lastFrame
        colorScheme = target
        outgoingAlpha.animateTo(0f, animationSpec)
        outgoingFrame = null
    }

    internal fun Modifier.drawTransition(): Modifier = drawWithContent {
        val content = this
        layer.record {
            content.drawContent()
            outgoingFrame?.let { drawImage(it, alpha = outgoingAlpha.value) }
        }
        drawLayer(layer)
    }
}

/** Keeps [target] as the scheme to reach, fading into it each time it changes. */
@Composable
internal fun rememberThemeTransition(
    target: ColorScheme,
    animationSpec: AnimationSpec<Float>,
): ThemeTransitionState {
    val layer = rememberGraphicsLayer()
    val state = remember(layer) { ThemeTransitionState(target, layer) }
    LaunchedEffect(state, target) { state.transitionTo(target, animationSpec) }
    return state
}

/** Draws the content through [state], so the outgoing frame can be kept and faded over it. */
internal fun Modifier.themeTransition(state: ThemeTransitionState): Modifier =
    with(state) { drawTransition() }

/**
 * [base], with its effects specs snapping colors whenever [isFading]. The type argument is erased,
 * so colors are told apart by their four-channel vector; alphas and spatial specs keep [base].
 */
private class ThemeTransitionMotionScheme(val base: MotionScheme, val isFading: () -> Boolean) :
    MotionScheme by base {
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> =
        SnapColorsWhileFading(base.defaultEffectsSpec(), isFading)

    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> =
        SnapColorsWhileFading(base.fastEffectsSpec(), isFading)

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> =
        SnapColorsWhileFading(base.slowEffectsSpec(), isFading)
}

/** Decided once per animation, when it starts and the spec is vectorized. */
private class SnapColorsWhileFading<T>(
    private val base: FiniteAnimationSpec<T>,
    private val isFading: () -> Boolean,
) : FiniteAnimationSpec<T> {
    override fun <V : AnimationVector> vectorize(
        converter: TwoWayConverter<T, V>
    ): VectorizedFiniteAnimationSpec<V> {
        val animated = base.vectorize(converter)
        if (!isFading()) return animated
        val snapped = snap<T>().vectorize(converter)
        return object : VectorizedFiniteAnimationSpec<V> {
            private fun pick(value: V) = if (value is AnimationVector4D) snapped else animated

            override fun getValueFromNanos(
                playTimeNanos: Long,
                initialValue: V,
                targetValue: V,
                initialVelocity: V,
            ): V =
                pick(initialValue)
                    .getValueFromNanos(playTimeNanos, initialValue, targetValue, initialVelocity)

            override fun getVelocityFromNanos(
                playTimeNanos: Long,
                initialValue: V,
                targetValue: V,
                initialVelocity: V,
            ): V =
                pick(initialValue)
                    .getVelocityFromNanos(playTimeNanos, initialValue, targetValue, initialVelocity)

            override fun getDurationNanos(
                initialValue: V,
                targetValue: V,
                initialVelocity: V,
            ): Long =
                pick(initialValue).getDurationNanos(initialValue, targetValue, initialVelocity)
        }
    }
}
