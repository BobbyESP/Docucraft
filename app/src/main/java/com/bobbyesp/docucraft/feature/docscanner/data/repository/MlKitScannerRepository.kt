package com.bobbyesp.docucraft.feature.docscanner.data.repository

import android.app.Activity
import androidx.activity.result.ActivityResult
import com.bobbyesp.docucraft.feature.docscanner.data.mapper.MlKitScannerMapper.toRawScanResult
import com.bobbyesp.docucraft.feature.docscanner.domain.exception.ScannerException
import com.bobbyesp.docucraft.feature.docscanner.domain.model.RawScanResult
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.ScannerRepository
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/**
 * Implementation of [ScannerRepository] that utilizes the Google ML Kit Document Scanner API.
 *
 * This repository handles the processing of [ActivityResult] data returned by the ML Kit
 * scanning activity. It maps the underlying [GmsDocumentScanningResult] to the domain's
 * [RawScanResult] and provides unified error handling for scan cancellations or technical failures.
 */
class MlKitScannerRepository : ScannerRepository {
    override suspend fun processResult(result: ActivityResult): Result<RawScanResult> {
        return try {
            val gmsResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)

            if (gmsResult == null) {
                throw ScannerException.ScanCancelled()
            } else {
                if (result.resultCode == Activity.RESULT_OK) {
                    Result.success(gmsResult.toRawScanResult())
                } else {
                    Result.failure(ScannerException.ScanCancelled())
                }
            }

        } catch (e: Exception) {
            Result.failure(ScannerException.ScanFailed(e.message ?: "Unknown error"))
        }
    }
}