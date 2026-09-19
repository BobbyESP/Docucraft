/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.scanner

/** An output a scanner engine can be asked to produce. */
enum class ScanOutputFormat {
    PDF,
    JPEG,
}

/**
 * What the caller wants out of a capture session.
 *
 * An engine is free to do less than it is asked: check [ScannerCapabilities] beforehand rather than
 * assuming every request is honoured.
 *
 * @property outputs Formats to produce.
 * @property maxPages Cap on captured pages, or `null` for no cap.
 * @property allowGalleryImport Whether the user may pick existing images instead of capturing.
 */
data class ScanRequest(
    val outputs: Set<ScanOutputFormat> = setOf(ScanOutputFormat.PDF),
    val maxPages: Int? = null,
    val allowGalleryImport: Boolean = true,
)
