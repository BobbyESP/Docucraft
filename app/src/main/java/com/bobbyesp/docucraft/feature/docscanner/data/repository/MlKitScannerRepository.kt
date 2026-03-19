package com.bobbyesp.docucraft.feature.docscanner.data.repository

import android.app.Activity
import androidx.activity.result.ActivityResult
import com.bobbyesp.docucraft.feature.docscanner.data.mapper.MlKitMapper
import com.bobbyesp.docucraft.feature.docscanner.domain.exception.ScannerException
import com.bobbyesp.docucraft.feature.docscanner.domain.model.RawScanResult
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.ScannerRepository
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

class MlKitScannerRepository : ScannerRepository {
    override suspend fun processResult(result: ActivityResult): Result<RawScanResult> {
        return try {
            val gmsResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)

            if (gmsResult == null) {
                throw ScannerException.ScanCancelled()
            } else {
                if (result.resultCode == Activity.RESULT_OK) {
                    Result.success(MlKitMapper.mapToDomain(gmsResult))
                } else {
                    Result.failure(ScannerException.ScanCancelled())
                }
            }

        } catch (e: Exception) {
            Result.failure(ScannerException.ScanFailed(e.message ?: "Unknown error"))
        }
    }

}