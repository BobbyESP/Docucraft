/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.lightColorScheme
import io.mockk.mockk
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
        val state = ThemeTransitionState(lightColorScheme(), layer = mockk(relaxed = true))
        val scheme = state.motionScheme(MotionScheme.expressive())

        assertSame(scheme, state.motionScheme(MotionScheme.expressive()))
        assertSame(scheme.defaultEffectsSpec<Float>(), scheme.defaultEffectsSpec<Float>())
        assertSame(scheme.fastEffectsSpec<Float>(), scheme.fastEffectsSpec<Float>())
        assertSame(scheme.slowEffectsSpec<Float>(), scheme.slowEffectsSpec<Float>())
        assertSame(scheme.defaultSpatialSpec<Float>(), scheme.defaultSpatialSpec<Float>())
    }
}
