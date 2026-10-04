/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.components.organization

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftAccentTheme
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
import com.bobbyesp.docucraft.feature.docscanner.domain.model.FolderIcon
import com.bobbyesp.docucraft.feature.docscanner.domain.model.LabelColor
import com.materialkolor.blend.Blend
import com.materialkolor.hct.Hct

/**
 * What a [LabelColor] looks like in the current theme: an accent for a dot or an icon on the page,
 * and a container with the content color that goes on it.
 */
@Immutable data class LabelTones(val accent: Color, val container: Color, val onContainer: Color)

/**
 * The tones of this color, or the theme's primary ones for no color.
 *
 * A palette color is a hue, not a value: it is pulled towards the theme's primary so that it sits
 * beside the user's own colors, and its tones are chosen as Material chooses a container's, lighter
 * in a light theme and darker in a dark one. The same key therefore never clashes with a wallpaper
 * color it was not picked next to.
 */
@Composable
fun LabelColor?.tones(): LabelTones {
    val colorScheme = MaterialTheme.colorScheme
    if (this == null) {
        return LabelTones(
            accent = colorScheme.primary,
            container = colorScheme.primaryContainer,
            onContainer = colorScheme.onPrimaryContainer,
        )
    }
    val primary = colorScheme.primary
    val dark = colorScheme.surface.luminance() < 0.5f
    return remember(this, primary, dark) {
        val seed = Hct.from(hue, SeedChroma, 50.0).toInt()
        val harmonized = Hct.fromInt(Blend.harmonize(seed, primary.toArgb()))
        fun tone(tone: Double, chroma: Double = harmonized.chroma) =
            Color(Hct.from(harmonized.hue, chroma, tone).toInt())
        if (dark) {
            LabelTones(
                accent = tone(80.0),
                container = tone(30.0, ContainerChroma),
                onContainer = tone(90.0, ContainerChroma),
            )
        } else {
            LabelTones(
                accent = tone(40.0),
                container = tone(90.0, ContainerChroma),
                onContainer = tone(10.0, ContainerChroma),
            )
        }
    }
}

/**
 * The app's theme around this color, for a screen about something that carries it, such as a
 * folder: [content] takes the color, and the rest of the app keeps its own. No color leaves the
 * theme as it is (see [DocucraftAccentTheme]).
 */
@Composable
fun LabelColorTheme(color: LabelColor?, content: @Composable () -> Unit) {
    DocucraftAccentTheme(
        accent = color?.let { Color(Hct.from(it.hue, SeedChroma, 50.0).toInt()) },
        content = content,
    )
}

private const val SeedChroma = 56.0
private const val ContainerChroma = 32.0

/** Where each color of the palette is on the HCT color wheel. */
private val LabelColor.hue: Double
    get() =
        when (this) {
            LabelColor.RED -> 25.0
            LabelColor.TERRACOTTA -> 45.0
            LabelColor.AMBER -> 80.0
            LabelColor.LIME -> 120.0
            LabelColor.GREEN -> 145.0
            LabelColor.TEAL -> 190.0
            LabelColor.SKY -> 235.0
            LabelColor.INDIGO -> 275.0
            LabelColor.VIOLET -> 310.0
            LabelColor.PINK -> 350.0
        }

/** The name of a color, for a screen reader: a swatch says nothing by itself. */
@get:StringRes
val LabelColor.label: Int
    get() =
        when (this) {
            LabelColor.RED -> R.string.color_red
            LabelColor.TERRACOTTA -> R.string.color_terracotta
            LabelColor.AMBER -> R.string.color_amber
            LabelColor.LIME -> R.string.color_lime
            LabelColor.GREEN -> R.string.color_green
            LabelColor.TEAL -> R.string.color_teal
            LabelColor.SKY -> R.string.color_sky
            LabelColor.INDIGO -> R.string.color_indigo
            LabelColor.VIOLET -> R.string.color_violet
            LabelColor.PINK -> R.string.color_pink
        }

val FolderIcon.imageVector: ImageVector
    get() =
        when (this) {
            FolderIcon.FOLDER -> Icons.Rounded.Folder
            FolderIcon.RECEIPT -> Icons.AutoMirrored.Rounded.ReceiptLong
            FolderIcon.WORK -> Icons.Rounded.Work
            FolderIcon.HOME -> Icons.Rounded.Home
            FolderIcon.SCHOOL -> Icons.Rounded.School
            FolderIcon.HEALTH -> Icons.Rounded.MedicalServices
            FolderIcon.BANK -> Icons.Rounded.AccountBalance
            FolderIcon.TRAVEL -> Icons.Rounded.Flight
            FolderIcon.CAR -> Icons.Rounded.DirectionsCar
            FolderIcon.IDENTITY -> Icons.Rounded.Badge
            FolderIcon.LEGAL -> Icons.Rounded.Gavel
            FolderIcon.FAMILY -> Icons.Rounded.FamilyRestroom
            FolderIcon.HEART -> Icons.Rounded.Favorite
            FolderIcon.STAR -> Icons.Rounded.Star
        }

@get:StringRes
val FolderIcon.label: Int
    get() =
        when (this) {
            FolderIcon.FOLDER -> R.string.icon_folder
            FolderIcon.RECEIPT -> R.string.icon_receipt
            FolderIcon.WORK -> R.string.icon_work
            FolderIcon.HOME -> R.string.icon_home
            FolderIcon.SCHOOL -> R.string.icon_school
            FolderIcon.HEALTH -> R.string.icon_health
            FolderIcon.BANK -> R.string.icon_bank
            FolderIcon.TRAVEL -> R.string.icon_travel
            FolderIcon.CAR -> R.string.icon_car
            FolderIcon.IDENTITY -> R.string.icon_identity
            FolderIcon.LEGAL -> R.string.icon_legal
            FolderIcon.FAMILY -> R.string.icon_family
            FolderIcon.HEART -> R.string.icon_heart
            FolderIcon.STAR -> R.string.icon_star
        }

/** The app's theme, and the same content in the theme of three colors of the palette. */
@PreviewLightDark
@Composable
private fun LabelColorThemePreview() {
    DocucraftTheme {
        Column {
            for (color in listOf(null, LabelColor.TEAL, LabelColor.AMBER, LabelColor.PINK)) {
                LabelColorTheme(color = color) {
                    Surface(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            FolderBadge(icon = FolderIcon.Default, color = null)
                            Text(
                                text = stringResource(color?.label ?: R.string.color_default),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Button(onClick = {}) { Text(text = stringResource(R.string.scan)) }
                        }
                    }
                }
            }
        }
    }
}
