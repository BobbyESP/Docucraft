/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A catalogued document, as Room stores it.
 *
 * Column names are the schema, so they stay as they are even where the domain has since settled on
 * clearer words. `ScannedDocumentMapper` is where the two vocabularies meet.
 */
@Entity(tableName = "scanned_documents")
data class ScannedDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
