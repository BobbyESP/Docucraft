/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bobbyesp.docucraft.core.designsystem.theme.DocucraftAccentTheme
import com.bobbyesp.docucraft.core.designsystem.theme.DocucraftTheme
import com.bobbyesp.docucraft.core.domain.model.ThemeConfig
import com.bobbyesp.docucraft.core.domain.model.UserPreferences
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A screen in a color of its own is recomposed once per scheme it shows: never for each step of a
 * change of the app's theme, which it would otherwise go through while the navigation animates it.
 */
@RunWith(AndroidJUnit4::class)
class AccentThemeRecompositionTest {

    @get:Rule val rule = createComposeRule()

    private var preferences by mutableStateOf(preferencesWithSeed(0xFF6200EE.toInt()))
    private var accent by mutableStateOf<Color?>(null)
    private val primaries = mutableListOf<Color>()

    @Test
    fun aChangeOfTheAppsThemeRecomposesTheScreenOnce() {
        accent = Color(0xFF2E7D32)
        showScreen()
        val before = primaries.last()

        primaries.clear()
        preferences = preferencesWithSeed(0xFFB3261E.toInt())
        rule.waitUntil(TimeoutMillis) {
            rule.runOnIdle { primaries.isNotEmpty() && primaries.last() != before }
        }
        rule.waitForIdle()

        assertTrue("recomposed for $primaries", primaries.size == 1)
    }

    @Test
    fun aColorArrivingAfterTheScreenRecomposesItOnce() {
        showScreen()
        val appPrimary = primaries.last()

        primaries.clear()
        accent = Color(0xFF2E7D32)
        rule.waitUntil(TimeoutMillis) {
            rule.runOnIdle { primaries.isNotEmpty() && primaries.last() != appPrimary }
        }
        rule.waitForIdle()

        assertTrue("recomposed for $primaries", primaries.size == 1)
    }

    private fun showScreen() {
        rule.setContent {
            DocucraftTheme(userPreferences = preferences) {
                DocucraftAccentTheme(accent = accent) { Screen() }
            }
        }
        // The accent's scheme is built in the background: wait until it is the one shown.
        rule.waitUntil(TimeoutMillis) {
            rule.runOnIdle { primaries.size >= 2 || accent == null && primaries.isNotEmpty() }
        }
        rule.waitForIdle()
    }

    @Composable
    private fun Screen() {
        val primary = MaterialTheme.colorScheme.primary
        SideEffect { primaries += primary }
    }

    private fun preferencesWithSeed(seed: Int) =
        UserPreferences(
            themeConfig = ThemeConfig.DARK,
            useDynamicColoring = false,
            themeSeedColor = seed,
        )

    private companion object {
        const val TimeoutMillis = 5_000L
    }
}
