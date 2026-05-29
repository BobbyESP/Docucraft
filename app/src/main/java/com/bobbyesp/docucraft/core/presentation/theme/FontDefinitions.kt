/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.bobbyesp.docucraft.R

val GoogleFontsProvider =
    GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = R.array.com_google_android_gms_fonts_certs,
    )

fun createGoogleFontFamily(fontName: String): FontFamily {
    val font = GoogleFont(fontName)
    return FontFamily(
        Font(googleFont = font, fontProvider = GoogleFontsProvider, weight = FontWeight.Normal),
        Font(googleFont = font, fontProvider = GoogleFontsProvider, weight = FontWeight.Medium),
        Font(googleFont = font, fontProvider = GoogleFontsProvider, weight = FontWeight.SemiBold),
        Font(googleFont = font, fontProvider = GoogleFontsProvider, weight = FontWeight.Bold),
    )
}
