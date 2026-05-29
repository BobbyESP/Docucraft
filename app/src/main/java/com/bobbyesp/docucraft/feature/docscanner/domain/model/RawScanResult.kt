/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class RawScanResult(
    val uri: String,
    val pageCount: Int = -1,
    val timestamp: Long = System.currentTimeMillis(),
)
