package com.bobbyesp.docucraft.feature.pdfscanner.domain.model

import androidx.compose.runtime.Immutable
import com.bobbyesp.docucraft.feature.pdfscanner.data.local.db.entity.ScannedPdfEntity
import kotlinx.serialization.Serializable

/**
 * Represents a scanned PDF document.
 *
 * This data class holds information about a scanned PDF file, including its name,
 * location, creation time, size, number of pages, and a thumbnail image.
 *
 * @property filename The name of the PDF file (e.g., "document.pdf").
 * @property title The title of the PDF document.
 * @property description A description of the PDF document.
 * @property path The Uri representing the location of the PDF file on the device's storage.
 * @property createdTimestamp The timestamp (in milliseconds) when the PDF file was created.
 * @property fileSize The size of the PDF file in bytes.
 * @property pageCount The number of pages in the PDF document.
 * @property thumbnail The Uri representing the location of the thumbnail image for the PDF file.
 */
@Serializable
@Immutable
data class ScannedPdf(
    val filename: String,
    val title: String?,
    val description: String?,
    val path: String,
    val createdTimestamp: Long,
    val fileSize: Long,
    val pageCount: Int,
    val thumbnail: String?
) {
    fun ScannedPdfEntity.toModel(): ScannedPdf {
        return ScannedPdf(
            filename = filename,
            title = title,
            description = description,
            path = path,
            createdTimestamp = createdTimestamp,
            fileSize = fileSize,
            pageCount = pageCount,
            thumbnail = thumbnail
        )
    }
}
