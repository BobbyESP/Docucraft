package com.bobbyesp.docucraft.domain.model

import android.net.Uri
import androidx.compose.runtime.Immutable
import com.bobbyesp.docucraft.data.local.db.entity.SavedPdfEntity

@Immutable
data class SavedPdf(
    val fileName: String,
    val path: Uri?,
    val savedTimestamp: Long,
    val fileSizeBytes: Long?,
    val pageCount: Int,
    val title: String?,
    val description: String?
) {
    fun toSavedPdfEntity() = SavedPdfEntity(
        savedTimestamp = savedTimestamp,
        fileName = fileName,
        path = path,
        fileSizeBytes = fileSizeBytes,
        pageCount = pageCount,
        title = title,
        description = description
    )
    companion object {
        fun emptyPdf(
            fileName: String = "",
            path: Uri? = null,
            savedTimestamp: Long = 0,
            fileSizeBytes: Long? = null,
            pageCount: Int = 0,
            title: String? = null,
            description: String? = null
        ): SavedPdf = SavedPdf(fileName, path, savedTimestamp, fileSizeBytes, pageCount, title, description)
    }
}
