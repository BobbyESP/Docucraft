package com.bobbyesp.docucraft.feature.docscanner.data.local.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.bobbyesp.docucraft.feature.docscanner.data.local.db.dao.ScannedDocumentDao
import com.bobbyesp.docucraft.feature.docscanner.data.local.db.entity.ScannedDocumentEntity

// TODO: Add labels to the database. The user might want to categorize the scanned PDFs.

const val CURRENT_VERSION = 2

@Database(
    entities = [ScannedDocumentEntity::class],
    version = CURRENT_VERSION,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = DocumentsDatabaseMigrations.Migration1To2::class)
                     ],
)
abstract class DocumentsDatabase : RoomDatabase() {
    abstract fun scannedDocumentDao(): ScannedDocumentDao
}
