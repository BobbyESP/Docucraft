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
 * Takes the app from one color scheme to the next with a single crossfade drawn over the whole
 * tree.
 *
 * Material provides the color scheme through a static composition local, so each new scheme
 * recomposes everything below the theme. Animating the scheme itself, as MaterialKolor's `animate =
 * true` does, hands the tree a new scheme on every frame, and the whole screen recomposes some
 * twenty times per theme change. Here it recomposes once: the last frame drawn with the old scheme
 * is kept as an image, the new scheme is applied, and the image fades out on top. The fade is read
 * only while drawing, so each of its frames costs a redraw of one node and nothing else.
 *
 * What is drawn in another window, such as a dialog, switches without the fade.
 */
@Stable
internal class ThemeTransitionState(initialScheme: ColorScheme, private val layer: GraphicsLayer) {

    /** The scheme to give Material: the new one is applied only once the old frame is kept. */
    var colorScheme: ColorScheme by mutableStateOf(initialScheme)
        private set

    private var outgoingFrame: ImageBitmap? by mutableStateOf(null)
    private val outgoingAlpha = Animatable(0f)

    /**
     * The motion scheme to give Material: [base], except that color animations starting while a
     * frame fades snap.
     *
     * It is the same object for the same [base], and asks whether a frame is fading only when an
     * animation starts, so neither the start nor the end of a fade recomposes anything.
     */
    fun motionScheme(base: MotionScheme): MotionScheme =
        motionSchemes?.takeIf { it.base === base }
            ?: ThemeTransitionMotionScheme(base, isFading = { outgoingFrame != null }).also {
                motionSchemes = it
            }

    private var motionSchemes: ThemeTransitionMotionScheme? = null

    internal suspend fun transitionTo(target: ColorScheme, animationSpec: AnimationSpec<Float>) {
        if (target == colorScheme) return
        // Nothing drawn yet (the first frame, a window not shown), so there is nothing to fade.
        if (layer.isReleased || layer.size == IntSize.Zero) {
            colorScheme = target
            return
        }
        // The layer holds the image still fading, if any, so a change made during another one
        // continues from what is on screen instead of jumping.
        val lastFrame = layer.toImageBitmap()
        outgoingAlpha.snapTo(1f)
        // Set together, so the frame that shows the new scheme is the one that starts covering it.
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
 * [base], with its effects specs snapping colors whenever [isFading].
 *
 * The type argument is erased, so a color is told apart by its vector: colors animate as four
 * channels, while the rest of what an effects spec drives (an alpha, for instance) is one value and
 * keeps [base]'s motion, as do all spatial specs.
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

/** Decided once per animation, as it starts: that is when a spec is vectorized. */
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
