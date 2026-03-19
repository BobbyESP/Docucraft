package com.bobbyesp.docucraft.feature.docscanner.data.mapper

import android.net.Uri
import com.bobbyesp.docucraft.feature.docscanner.domain.model.RawScanResult
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

object MlKitScannerMapper {

    fun GmsDocumentScanningResult.toRawScanResult(): RawScanResult {
        val uri = this.pdf?.uri ?: Uri.EMPTY
        val pageCount = this.pdf?.pageCount ?: 0

        return RawScanResult(uri = uri.toString(), pageCount = pageCount)
    }
}