/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.bobbyesp.docucraft.core.domain.model.FontConfig
import com.bobbyesp.docucraft.core.domain.model.PaletteStyleConfig
import com.bobbyesp.docucraft.core.domain.model.ThemeConfig
import com.materialkolor.PaletteStyle

private val DMSerifDisplayFamily = FontFamily(BundledFonts.DMSerifDisplay)
private val DMSerifTextFamily = FontFamily(BundledFonts.DMSerifText)
private val InterFamily = FontFamily(BundledFonts.Inter)
private val DMSansFamily = FontFamily(BundledFonts.DMSans)
private val JetBrainsMonoFamily = FontFamily(BundledFonts.JetBrainsMono)

private val googleFontCache = mutableMapOf<FontConfig, FontFamily>()

private fun FontConfig.downloadable(name: String, fallback: List<Font>): FontFamily =
    googleFontCache.getOrPut(this) { createGoogleFontFamily(name, fallback) }

/**
 * Maps the domain [FontConfig] to the UI [FontFamily].
 *
 * A font packaged with the app is always used from `res/font`, so the defaults never touch Google
 * Fonts and are ready on the first frame. Only the others are downloaded, and each shows a bundled
 * font of its kind while it loads.
 */
fun FontConfig.toFontFamily(): FontFamily? =
    when (this) {
        FontConfig.System -> null
        FontConfig.DMSerifDisplay -> DMSerifDisplayFamily
        FontConfig.DMSerifText -> DMSerifTextFamily
        FontConfig.Inter -> InterFamily
        FontConfig.DMSans -> DMSansFamily
        FontConfig.JetBrainsMono -> JetBrainsMonoFamily
        FontConfig.GoogleSansFlex -> downloadable("Google Sans Flex", BundledFonts.Inter)
        FontConfig.Roboto -> downloadable("Roboto", BundledFonts.Inter)
        FontConfig.Montserrat -> downloadable("Montserrat", BundledFonts.Inter)
        FontConfig.FiraCode -> downloadable("Fira Code", BundledFonts.JetBrainsMono)
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
