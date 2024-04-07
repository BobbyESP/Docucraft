package com.bobbyesp.docucraft.domain.model

import android.net.Uri

data class SavedPdf(
    val fileName: String,
    val path: Uri?,
    val savedTimestamp: Long,
    val fileSizeBytes: Long?,
    val pageCount: Int,
    val title: String?,
    val description: String?
) {
    companion object {
        fun emptyPdf(): SavedPdf = SavedPdf("", null, 0, null, 0, null, null)
    }
}
