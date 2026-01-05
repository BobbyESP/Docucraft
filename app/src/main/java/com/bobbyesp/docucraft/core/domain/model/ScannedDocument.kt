package com.bobbyesp.docucraft.core.domain.model

/**
 * Represents a document that has been scanned.
 * This model is agnostic of the scanning implementation (ML Kit, etc).
 */
data class ScannedDocument(
    val uriString: String,
    val pageCount: Int = 1,
    val timestamp: Long = System.currentTimeMillis()
)
