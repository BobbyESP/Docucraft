package com.bobbyesp.docucraft.feature.docscanner.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "scanned_documents")
data class ScannedDocumentEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val filename: String,
    val title: String?,
    val description: String?,
    val path: String,
    val createdTimestamp: Long,
    val fileSize: Long,
    val pageCount: Int,
    val thumbnail: String?,
)
