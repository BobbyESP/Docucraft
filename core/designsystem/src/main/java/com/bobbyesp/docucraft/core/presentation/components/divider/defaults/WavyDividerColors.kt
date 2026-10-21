/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.components.divider.defaults

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color

@Immutable
class WavyDividerColors internal constructor(private val containerColor: Color) {
    @Composable
    internal fun color(): State<Color> {
        return rememberUpdatedState(containerColor)
    }
}
