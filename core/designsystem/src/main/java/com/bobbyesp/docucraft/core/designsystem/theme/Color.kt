/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.designsystem.theme

import android.content.Context
import android.os.Build

fun isDynamicColoringSupported(): Boolean {
    return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
}

/**
 * The color the system draws its own palette from, taken from the wallpaper or picked by the user,
 * or `null` below Android 12. Only its hue is a seed: the app's palette style sets the rest.
 */
fun wallpaperSeedColor(context: Context): Int? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getColor(android.R.color.system_accent1_500)
    } else {
        null
    }
