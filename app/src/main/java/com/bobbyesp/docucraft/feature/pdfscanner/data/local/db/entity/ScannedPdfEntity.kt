package com.bobbyesp.docucraft.feature.pdfscanner.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@Entity(tableName = "scanned_pdfs")
data class ScannedPdfEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val filename: String,
    val title: String?,
    val description: String?,
    val path: String,
    val createdTimestamp: Long,
    val fileSize: Long,
    val pageCount: Int,
    val thumbnail: String?
)