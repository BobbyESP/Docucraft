/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.repository

import android.app.Activity
import androidx.activity.result.ActivityResult
import com.bobbyesp.docucraft.feature.docscanner.data.mapper.ScanResultMapper
import com.bobbyesp.docucraft.feature.docscanner.domain.exception.ScannerException
import com.bobbyesp.docucraft.feature.docscanner.domain.model.RawScanResult
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.ScannerRepository
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

class MlKitScannerRepository(private val clock: () -> Long = System::currentTimeMillis) :
    ScannerRepository {

    override suspend fun processResult(result: ActivityResult): Result<RawScanResult> {
        // Reading the engine payload is the only part that can throw, so it is the only part
        // wrapped: what the payload means is decided by ScanResultMapper. Keeping the two apart is
        // what stops a cancellation from being swallowed by this catch and re-reported as a
        // failure.
        val scan =
            try {
                GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            } catch (e: Exception) {
                return Result.failure(ScannerException.ScanFailed(e.message ?: "Unknown error"))
            }

        // No payload at all means the scanner came back without a scan, which is what backing out
        // of it looks like.
        return ScanResultMapper.map(
            completed = result.resultCode == Activity.RESULT_OK && scan != null,
            pdfUri = scan?.pdf?.uri?.toString(),
            pageCount = scan?.pdf?.pageCount,
            timestamp = clock(),
        )
    }
}
