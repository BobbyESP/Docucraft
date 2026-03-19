package com.bobbyesp.docucraft.feature.docscanner.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = ScannedDocumentEntity::class)
@Entity(tableName = "scanned_documents_fts")
data class ScannedDocumentFtsEntity(
    @ColumnInfo(name = "title")
    val title: String?,
    @ColumnInfo(name = "description")
    val description: String?,
    @ColumnInfo(name = "filename")
    val filename: String
)