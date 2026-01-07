package com.bobbyesp.docucraft.core.data.mapper

import android.net.Uri
import com.bobbyesp.docucraft.core.domain.model.ScannedDocument
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/**
 * Mapper to convert ML Kit objects into Domain models. This ensures the Domain layer never sees ML
 * Kit classes.
 */
object MlKitMapper {

    fun mapToDomain(result: GmsDocumentScanningResult): ScannedDocument {
        // Prefer PDF URI if available, otherwise use the first page's URI
        val uri = result.pdf?.uri ?: result.pages?.firstOrNull()?.imageUri ?: Uri.EMPTY
        val pageCount = result.pages?.size ?: 0

        return ScannedDocument(uriString = uri.toString(), pageCount = pageCount)
    }
}
