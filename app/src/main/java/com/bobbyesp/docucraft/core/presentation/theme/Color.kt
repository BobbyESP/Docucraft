package com.bobbyesp.docucraft.core.presentation.theme

import android.os.Build

const val DEFAULT_SEED_COLOR = 0xFF6200EE.toInt()

fun isDynamicColoringSupported(): Boolean {
    return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
}