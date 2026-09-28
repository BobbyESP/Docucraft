/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font as GoogleFontFile
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.bobbyesp.docucraft.R

/** The weights the type scale asks for (see [createTypography]). */
private val TypeScaleWeights =
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

/**
 * Fonts packaged in `res/font`. They load synchronously on the first frame, with no provider and no
 * network, so the default typography never swaps its font after the app starts.
 */
internal object BundledFonts {
    val DMSerifDisplay: List<Font> = singleWeight(R.font.dm_serif_display_regular)
    val DMSerifText: List<Font> = singleWeight(R.font.dm_serif_text_regular)
    val Inter: List<Font> =
        listOf(
            Font(R.font.inter_regular, FontWeight.Normal),
            Font(R.font.inter_medium, FontWeight.Medium),
            Font(R.font.inter_semibold, FontWeight.SemiBold),
            Font(R.font.inter_bold, FontWeight.Bold),
        )
    val DMSans: List<Font> =
        listOf(
            Font(R.font.dm_sans_regular, FontWeight.Normal),
            Font(R.font.dm_sans_medium, FontWeight.Medium),
            Font(R.font.dm_sans_semibold, FontWeight.SemiBold),
            Font(R.font.dm_sans_bold, FontWeight.Bold),
        )
    val JetBrainsMono: List<Font> =
        listOf(
            Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
            Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
            Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
            Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
        )

    /**
     * A family published in one weight only, declared at every weight of the type scale, as its
     * downloadable version is. Declared only as regular, Compose would synthesize a fake bold for
     * the headlines and titles that use it.
     */
    private fun singleWeight(resId: Int): List<Font> = TypeScaleWeights.map { weight ->
        Font(resId, weight)
    }
}

private val GoogleFontsProvider =
    GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = R.array.com_google_android_gms_fonts_certs,
    )

/**
 * A family downloaded from Google Fonts. It loads asynchronously: until it arrives, or if it never
 * does, text is drawn with [fallback], a bundled font of the same kind, and then swaps once.
 * Compose uses the first font it can load at once after the pending downloadable ones, which is why
 * [fallback] goes last.
 */
fun createGoogleFontFamily(fontName: String, fallback: List<Font> = emptyList()): FontFamily {
    val font = GoogleFont(fontName)
    val downloadable = TypeScaleWeights.map { weight ->
        GoogleFontFile(googleFont = font, fontProvider = GoogleFontsProvider, weight = weight)
    }
    return FontFamily(downloadable + fallback)
}
