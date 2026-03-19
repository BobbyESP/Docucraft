package com.bobbyesp.docucraft.feature.docscanner.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.bobbyesp.docucraft.feature.docscanner.data.db.dao.ScannedDocumentDao
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.ScannedDocumentEntity
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.ScannedDocumentFtsEntity

const val CURRENT_VERSION = 3

@Database(
    entities = [ScannedDocumentEntity::class, ScannedDocumentFtsEntity::class],
    version = CURRENT_VERSION,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = DocumentsDatabaseMigrations.Migration1To2::class),
    ],
)
abstract class DocumentsDatabase : RoomDatabase() {
    abstract fun scannedDocumentDao(): ScannedDocumentDao
}
