/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.scanner

/**
 * What a given scanner engine can do, so callers can adapt without knowing which engine they got.
 *
 * @property supportedOutputs Formats this engine can produce.
 * @property supportsGalleryImport Whether it can import existing images instead of capturing.
 * @property maxPages Hard cap on pages per session, or `null` if unbounded.
 * @property requiresCameraPermission Whether the app itself must hold the camera permission. False
 *   for engines that capture in their own process, which is why the app declares no camera
 *   permission today; an in-app engine would report true and need one granted first.
 */
data class ScannerCapabilities(
    val supportedOutputs: Set<ScanOutputFormat>,
    val supportsGalleryImport: Boolean,
    val maxPages: Int?,
    val requiresCameraPermission: Boolean,
)
