package com.bobbyesp.docucraft.feature.docscanner.data.mapper

import android.net.Uri
import com.bobbyesp.docucraft.feature.docscanner.domain.model.RawScanResult
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

object MlKitMapper {

    fun mapToDomain(result: GmsDocumentScanningResult): RawScanResult {
        val uri = result.pdf?.uri ?: Uri.EMPTY
        val pageCount = result.pdf?.pageCount ?: 0

        return RawScanResult(uri = uri.toString(), pageCount = pageCount)
    }
}