package com.bobbyesp.docucraft.feature.docscanner.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class RawScanResult(
    val uri: String,
    val pageCount: Int = -1,
    val timestamp: Long = System.currentTimeMillis(),
)