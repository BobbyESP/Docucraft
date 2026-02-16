package com.bobbyesp.docucraft.feature.docscanner.domain.model

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import com.bobbyesp.docucraft.core.util.UriSerializer
import com.bobbyesp.docucraft.feature.docscanner.data.local.db.entity.ScannedPdfEntity
import kotlinx.serialization.Serializable

/**
 * Represents a scanned PDF document.
 *
 * This data class holds information about a scanned PDF file, including its name, location,
 * creation time, size, number of pages, and a thumbnail image.
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
    val id: String,
    val filename: String,
    val title: String?,
    val description: String?,
    @Serializable(with = UriSerializer::class) val path: Uri,
    val createdTimestamp: Long,
    val fileSize: Long,
    val pageCount: Int,
    val thumbnail: String?,
) {
    companion object {
        fun ScannedPdfEntity.toModel(): ScannedPdf {
            return ScannedPdf(
                id = id,
                filename = filename,
                title = title,
                description = description,
                path = path.toUri(),
                createdTimestamp = createdTimestamp,
                fileSize = fileSize,
                pageCount = pageCount,
                thumbnail = thumbnail,
            )
        }
    }
}
