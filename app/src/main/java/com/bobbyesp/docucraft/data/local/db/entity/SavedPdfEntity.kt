package com.bobbyesp.docucraft.data.local.db.entity

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.bobbyesp.docucraft.domain.model.SavedPdf

@Immutable
@Entity(tableName = "saved_pdfs")
data class SavedPdfEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fileName: String,
    val path: Uri?,
    val savedTimestamp: Long,
    val fileSizeBytes: Long?,
    val pageCount: Int,
    val title: String?,
    val description: String?
) {
    fun toSavedPdf() = SavedPdf(
        savedTimestamp = savedTimestamp,
        fileName = fileName,
        path = path,
        fileSizeBytes = fileSizeBytes,
        pageCount = pageCount,
        title = title,
        description = description
    )
}
