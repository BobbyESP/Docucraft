package com.bobbyesp.docucraft.feature.docscanner.domain.model

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import com.bobbyesp.docucraft.core.util.UriSerializer
import com.bobbyesp.docucraft.feature.docscanner.data.local.db.entity.ScannedDocumentEntity
import kotlinx.serialization.Serializable

/**
 * Data class that represents a scanned document.
 *
 * This class is used to model the details of a scanned PDF document, including its metadata
 * and file-related information. It is marked as @Serializable to allow serialization and
 * deserialization, and as @Immutable to ensure immutability for Compose compatibility.
 *
 * @property id A unique identifier for the scanned document.
 * @property filename The name of the document file without extension (e.g., "internet_bill_january_2026").
 * @property title The title of the document. This field is optional and can be null.
 * @property description A brief description of the document. This field is optional and can be null.
 * @property path The Uri representing the location of the file on the device's storage.
 *                This property uses a custom serializer, `UriSerializer`, for proper serialization.
 * @property createdTimestamp The timestamp (in milliseconds) indicating when the file was created.
 * @property fileSize The size of the file in bytes.
 * @property pageCount The total number of pages in the document.
 * @property thumbnail The Uri (as a String) representing the location of the thumbnail image for the file.
 *                     This field is optional and can be null.
 */
@Serializable
@Immutable
data class ScannedDocument(
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
        /**
         * Extension function to map a `ScannedDocumentEntity` object to a `ScannedDocument` object.
         *
         * This function is used to convert a database entity representation of a scanned document
         * into its domain model representation.
         *
         * @receiver ScannedDocumentEntity The database entity to be converted.
         * @return ScannedDocument The domain model representation of the scanned document.
         */
        fun ScannedDocumentEntity.toModel(): ScannedDocument {
            return ScannedDocument(
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
