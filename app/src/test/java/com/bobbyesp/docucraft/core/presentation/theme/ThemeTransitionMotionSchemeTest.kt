/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.MonotonicFrameClock
import java.util.Collections
import java.util.IdentityHashMap
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class ThemeTransitionMotionSchemeTest {

    /**
     * Material keeps what it is animating by the spec it is handed. A scheme that built a new spec
     * on every call made each recomposition look like a change of spec, and a button pressed mid
     * morph started over from the shape it was heading to: it jumped instead of animating.
     */
    @Test
    fun `the theme's motion scheme hands out the same spec every time`() {
        val state = ThemeTransitionState(lightColorScheme())
        val scheme = state.motionScheme(MotionScheme.expressive())

        assertSame(scheme, state.motionScheme(MotionScheme.expressive()))
        assertSame(scheme.defaultEffectsSpec<Float>(), scheme.defaultEffectsSpec<Float>())
        assertSame(scheme.fastEffectsSpec<Float>(), scheme.fastEffectsSpec<Float>())
        assertSame(scheme.slowEffectsSpec<Float>(), scheme.slowEffectsSpec<Float>())
        assertSame(scheme.defaultSpatialSpec<Float>(), scheme.defaultSpatialSpec<Float>())
    }

    /**
     * Each scheme given to Material recomposes everything under the theme, so how many a change
     * goes through is its cost: it must not grow with the display's refresh rate.
     */
    @Test
    fun `a change goes through no more schemes than its steps, however many frames it has`() =
        runTest {
            val light = lightColorScheme()
            val dark = darkColorScheme()
            val state = ThemeTransitionState(light)
            val shown = FramesOf(state, frameNanos = 2_000_000L)

            withContext(shown) { state.transitionTo(dark) }

            assertSame(dark, state.colorScheme)
            assertEquals(
                "frames drawn: ${shown.frames}",
                ThemeTransitionState.Steps,
                (shown.schemes - light).size,
            )
        }

    @Test
    fun `a change that interrupts another starts from what is showing`() = runTest {
        val light = lightColorScheme()
        val dark = darkColorScheme()
        val state = ThemeTransitionState(light)
        // Cut short after a few frames, as a second change of theme cuts the first.
        val interrupted = FramesOf(state, frameNanos = 16_000_000L, stopAfter = 8)
        runCatching { withContext(interrupted) { state.transitionTo(dark) } }
        val midway = state.colorScheme

        assertNotEquals(light.surface, midway.surface)
        assertNotEquals(dark.surface, midway.surface)

        withContext(FramesOf(state, frameNanos = 16_000_000L)) { state.transitionTo(light) }

        assertSame(light, state.colorScheme)
    }

    @Test
    fun `a scheme between two is each of them at its ends`() {
        val light = lightColorScheme()
        val dark = darkColorScheme()

        assertEquals(light.primary, lerp(light, dark, 0f).primary)
        assertEquals(dark.surfaceContainerLow, lerp(light, dark, 1f).surfaceContainerLow)
        assertNotEquals(light.surface, lerp(light, dark, 0.5f).surface)
    }

    /** A display that draws a frame every [frameNanos], noting the scheme each one was drawn in. */
    private class FramesOf(
        private val state: ThemeTransitionState,
        private val frameNanos: Long,
        private val stopAfter: Int = Int.MAX_VALUE,
    ) : MonotonicFrameClock {
        val schemes: MutableSet<ColorScheme> = Collections.newSetFromMap(IdentityHashMap())
        var frames = 0
            private set

        private var now = 0L

        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            if (frames >= stopAfter) throw IllegalStateException("interrupted")
            frames++
            now += frameNanos
            return onFrame(now).also { schemes += state.colorScheme }
        }
    }
}
