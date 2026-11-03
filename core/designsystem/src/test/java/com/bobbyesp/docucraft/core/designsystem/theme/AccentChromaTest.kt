/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.designsystem.theme

import com.bobbyesp.docucraft.core.domain.model.PaletteStyleConfig
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeTonalSpot
import org.junit.Assert.assertEquals
import org.junit.Test

class AccentChromaTest {

    @Test
    fun `accent chroma is the chroma of an accent scheme's primary tones`() {
        assertEquals(PaletteStyleConfig.TonalSpot, AccentPaletteStyle)
        for (isDark in listOf(false, true)) {
            for (hue in listOf(25.0, 145.0, 235.0, 310.0)) {
                val scheme =
                    SchemeTonalSpot(
                        sourceColorHct = Hct.from(hue, 56.0, 50.0),
                        isDark = isDark,
                        contrastLevel = 0.0,
                        specVersion = DocucraftColorSpec,
                        platform = DynamicScheme.Platform.PHONE,
                    )
                assertEquals(
                    "isDark=$isDark hue=$hue",
                    scheme.primaryPalette.chroma,
                    accentChroma(isDark),
                    0.0,
                )
            }
        }
    }
}
