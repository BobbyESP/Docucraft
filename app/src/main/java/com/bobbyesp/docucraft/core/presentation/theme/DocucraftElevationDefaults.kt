package com.bobbyesp.docucraft.core.presentation.theme

import androidx.compose.ui.unit.dp

object DocucraftElevationDefaults {
    /** Nivel 0: Fondo de la aplicación (Scaffold) */
    val Level0 = 0.dp

    /** Nivel 1: Elevación mínima (Cards, Search bars) */
    val Level1 = 1.dp

    /** Nivel 2: Elevación estándar (Surface intermedio) */
    val Level2 = 3.dp

    /** Nivel 3: Elevación acentuada (Modals, Action Sheets) */
    val Level3 = 6.dp

    /** Nivel 4: Elevación máxima (FAB, Menus flotantes) */
    val Level4 = 8.dp

    val Scaffold = Level0
    val Card = Level1
    val Default = Level2
    val Modal = Level3
    val FAB = Level4
}