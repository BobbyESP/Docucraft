/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import com.bobbyesp.docucraft.core.domain.model.FontConfig
import com.bobbyesp.docucraft.core.domain.model.PaletteStyleConfig
import com.bobbyesp.docucraft.core.domain.model.ThemeConfig
import com.materialkolor.PaletteStyle

private val googleFontCache = mutableMapOf<String, FontFamily>()

private fun getCachedGoogleFont(name: String): FontFamily {
    return googleFontCache.getOrPut(name) { createGoogleFontFamily(name) }
}

/** Maps the domain [FontConfig] to the UI [FontFamily]. */
fun FontConfig.toFontFamily(): FontFamily? =
    when (this) {
        FontConfig.System -> null
        FontConfig.DMSerifText -> getCachedGoogleFont("DM Serif Text")
        FontConfig.GoogleSansFlex -> getCachedGoogleFont("Google Sans Flex")
        FontConfig.Inter -> getCachedGoogleFont("Inter")
        FontConfig.Roboto -> getCachedGoogleFont("Roboto")
        FontConfig.Montserrat -> getCachedGoogleFont("Montserrat")
        FontConfig.JetBrainsMono -> getCachedGoogleFont("JetBrains Mono")
        FontConfig.FiraCode -> getCachedGoogleFont("Fira Code")
    }

/** Maps the domain [ThemeConfig] to a boolean representing if the dark theme should be active. */
@Composable
fun ThemeConfig.isDarkTheme(): Boolean =
    when (this) {
        ThemeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        ThemeConfig.LIGHT -> false
        ThemeConfig.DARK -> true
    }

/** Maps the domain [PaletteStyleConfig] to the MaterialKolor [PaletteStyle]. */
fun PaletteStyleConfig.toPaletteStyle(): PaletteStyle =
    when (this) {
        PaletteStyleConfig.Vibrant -> PaletteStyle.Vibrant
        PaletteStyleConfig.Expressive -> PaletteStyle.Expressive
        PaletteStyleConfig.FruitSalad -> PaletteStyle.FruitSalad
        PaletteStyleConfig.Monochrome -> PaletteStyle.Monochrome
        PaletteStyleConfig.Rainbow -> PaletteStyle.Rainbow
        PaletteStyleConfig.TonalSpot -> PaletteStyle.TonalSpot
    }
