package com.bobbyesp.docucraft.core.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Creates a [Typography] object applying the given [fontFamily] to all text styles.
 * If [fontFamily] is null, the system default font is used.
 */
fun createTypography(
    displayFont: FontFamily?,
    titleFont: FontFamily?,
    bodyFont: FontFamily?,
    labelFont: FontFamily?
): Typography {
    val base = Typography()

    return Typography(
        /* DISPLAY */
        displayLarge = base.displayLarge.copy(
            fontFamily = displayFont,
            fontWeight = FontWeight.Normal
        ),
        displayMedium = base.displayMedium.copy(
            fontFamily = displayFont,
            fontWeight = FontWeight.Normal
        ),
        displaySmall = base.displaySmall.copy(
            fontFamily = displayFont,
            fontWeight = FontWeight.Normal
        ),

        /* HEADLINE */
        headlineLarge = base.headlineLarge.copy(
            fontFamily = displayFont,
            fontWeight = FontWeight.SemiBold
        ),
        headlineMedium = base.headlineMedium.copy(
            fontFamily = displayFont,
            fontWeight = FontWeight.SemiBold
        ),
        headlineSmall = base.headlineSmall.copy(
            fontFamily = displayFont,
            fontWeight = FontWeight.Medium
        ),

        /* TITLES */
        titleLarge = base.titleLarge.copy(
            fontFamily = titleFont,
            fontWeight = FontWeight.Bold
        ),
        titleMedium = base.titleMedium.copy(
            fontFamily = titleFont,
            fontWeight = FontWeight.Bold
        ),
        titleSmall = base.titleSmall.copy(
            fontFamily = titleFont,
            fontWeight = FontWeight.Medium
        ),

        /* BODY */
        bodyLarge = base.bodyLarge.copy(
            fontFamily = bodyFont,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.sp
        ),
        bodyMedium = base.bodyMedium.copy(
            fontFamily = bodyFont,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.sp
        ),
        bodySmall = base.bodySmall.copy(
            fontFamily = bodyFont,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.sp
        ),

        /* LABEL */
        labelLarge = base.labelLarge.copy(
            fontFamily = labelFont,
            fontWeight = FontWeight.Medium
        ),
        labelMedium = base.labelMedium.copy(
            fontFamily = labelFont,
            fontWeight = FontWeight.Medium
        ),
        labelSmall = base.labelSmall.copy(
            fontFamily = labelFont,
            fontWeight = FontWeight.Medium
        )
    )
}