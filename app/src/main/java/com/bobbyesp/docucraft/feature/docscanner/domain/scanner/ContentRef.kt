/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.scanner

/**
 * An opaque pointer to something a scanner engine produced.
 *
 * The domain never interprets it: only the data layer knows whether it is a content Uri, a file
 * path or something a future engine invents. Keeping it a plain string is what lets the scanning
 * contract be exercised without a device, since `android.net.Uri` cannot be built off-device.
 */
@JvmInline value class ContentRef(val value: String)
