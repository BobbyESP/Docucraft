/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.repository

import androidx.activity.result.ActivityResult
import com.bobbyesp.docucraft.feature.docscanner.domain.model.RawScanResult

interface ScannerRepository {
    suspend fun processResult(result: ActivityResult): Result<RawScanResult>
}
