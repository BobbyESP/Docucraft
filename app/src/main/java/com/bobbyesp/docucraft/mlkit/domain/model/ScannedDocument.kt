package com.bobbyesp.docucraft.mlkit.domain.model

import androidx.compose.runtime.Immutable

/**
 * Represents a document that has been scanned. This model is agnostic of the scanning
 * implementation (ML Kit, etc).
 */
@Immutable
data class ScannedDocument(
    val uriString: String,
    val pageCount: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
)
