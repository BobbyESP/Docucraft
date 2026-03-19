package com.bobbyesp.docucraft.feature.docscanner.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "scanned_documents")
data class ScannedDocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val filename: String,
    val title: String?,
    val description: String?,
    val path: String,
    val createdTimestamp: Long,
    val fileSize: Long,
    val pageCount: Int,
    val thumbnail: String?,
)

data class ScannedDocumentEntityWithMatchInfo(
    @Embedded val document: ScannedDocumentEntity,
    @ColumnInfo(name = "matchInfo") val matchInfo: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ScannedDocumentEntityWithMatchInfo

        if (document != other.document) return false
        if (!matchInfo.contentEquals(other.matchInfo)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = document.hashCode()
        result = 31 * result + matchInfo.contentHashCode()
        return result
    }
}