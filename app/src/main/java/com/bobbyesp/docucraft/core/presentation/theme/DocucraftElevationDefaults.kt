package com.bobbyesp.docucraft.core.presentation.theme

import androidx.compose.ui.unit.dp

object DocucraftElevationDefaults {
    /** Level 0: App background (Scaffold) */
    val Level0 = 0.dp

    /** Level 1: Minimum elevation (Cards, Search bars) */
    val Level1 = 1.dp

    /** Level 2: Standard elevation (intermediate Surface) */
    val Level2 = 3.dp

    /** Level 3: Accentuated/Bold elevation (Modals, Action Sheets) */
    val Level3 = 6.dp

    /** Level 4: Maximum elevation (FAB, Menus flotantes) */
    val Level4 = 8.dp

    val Scaffold = Level0
    val Card = Level1
    val Default = Level2
    val Modal = Level3
    val FAB = Level4
}