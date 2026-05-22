package com.bobbyesp.docucraft.core.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.bobbyesp.docucraft.R

/**
 * Creates a [Typography] object applying the given [fontFamily] to all text styles.
 * If [fontFamily] is null, the system default font is used.
 */
fun createTypography(fontFamily: FontFamily?): Typography {
    val base = Typography()

    return Typography(
        /* DISPLAY */
        displayLarge = base.displayLarge.copy(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal
        ),
        displayMedium = base.displayMedium.copy(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal
        ),
        displaySmall = base.displaySmall.copy(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal
        ),

        /* HEADLINE */
        headlineLarge = base.headlineLarge.copy(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold
        ),
        headlineMedium = base.headlineMedium.copy(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold
        ),
        headlineSmall = base.headlineSmall.copy(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium
        ),

        /* TITLES */
        titleLarge = base.titleLarge.copy(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold
        ),
        titleMedium = base.titleMedium.copy(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold
        ),
        titleSmall = base.titleSmall.copy(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium
        ),

        /* BODY */
        bodyLarge = base.bodyLarge.copy(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.sp
        ),
        bodyMedium = base.bodyMedium.copy(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.sp
        ),
        bodySmall = base.bodySmall.copy(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.sp
        ),

        /* LABEL */
        labelLarge = base.labelLarge.copy(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium
        ),
        labelMedium = base.labelMedium.copy(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium
        ),
        labelSmall = base.labelSmall.copy(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium
        )
    )
}