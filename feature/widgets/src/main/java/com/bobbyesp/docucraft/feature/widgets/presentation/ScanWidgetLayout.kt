/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.widgets.presentation

/**
 * Which of the scan widget's layouts fits the space the launcher gives it.
 *
 * A widget cannot measure itself: it is drawn in the launcher's process from a layout chosen here,
 * before anything is laid out. So the choice is made from the size the launcher reports, and kept
 * free of Android so that it can be tested as plain logic.
 */
internal enum class ScanWidgetLayout {
    /** The icon alone. */
    Small,

    /** The icon and the title in a row. */
    Wide,

    /** The title above, the button in the corner. */
    Large;

    companion object {
        /**
         * Two cells of any launcher's grid are at least this much, and one cell never is. Below it
         * the title would be cut after a few letters in most of the app's languages.
         */
        private const val ROOM_FOR_TEXT_DP = 130f

        fun forSize(widthDp: Float, heightDp: Float): ScanWidgetLayout =
            when {
                widthDp >= ROOM_FOR_TEXT_DP && heightDp >= ROOM_FOR_TEXT_DP -> Large
                widthDp >= ROOM_FOR_TEXT_DP -> Wide
                else -> Small
            }
    }
}
