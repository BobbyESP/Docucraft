/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.bobbyesp.docucraft.core.domain.model.PaletteStyleConfig
import com.materialkolor.blend.Blend
import com.materialkolor.dynamicColorScheme
import org.junit.Assert.assertEquals
import org.junit.Test

class AccentRolesTest {

    @Test
    fun `accent roles are the primary roles of the accent's scheme`() {
        val appPrimary = Color(0xFF6750A4)
        for (style in PaletteStyleConfig.entries) {
            for (isDark in listOf(false, true)) {
                for (accent in listOf(Color(0xFFC62828), Color(0xFF2E7D32), Color(0xFF0277BD))) {
                    val roles = accentRoles(accent, appPrimary, isDark, style)
                    val scheme =
                        dynamicColorScheme(
                            seedColor =
                                Color(Blend.harmonize(accent.toArgb(), appPrimary.toArgb())),
                            isDark = isDark,
                            isAmoled = false,
                            specVersion = DocucraftColorSpec,
                            style = style.toPaletteStyle(),
                        )
                    val case = "$style isDark=$isDark accent=$accent"
                    assertEquals(case, scheme.primary, roles.primary)
                    assertEquals(case, scheme.primaryContainer, roles.primaryContainer)
                    assertEquals(case, scheme.onPrimaryContainer, roles.onPrimaryContainer)
                }
            }
        }
    }
}
