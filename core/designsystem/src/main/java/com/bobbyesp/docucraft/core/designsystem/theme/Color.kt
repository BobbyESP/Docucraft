/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.designsystem.theme

import android.os.Build

fun isDynamicColoringSupported(): Boolean {
    return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
}
