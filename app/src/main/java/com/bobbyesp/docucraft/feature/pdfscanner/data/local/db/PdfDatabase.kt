package com.bobbyesp.docucraft.feature.pdfscanner.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.bobbyesp.docucraft.feature.pdfscanner.data.local.db.dao.ScannedPdfDao
import com.bobbyesp.docucraft.feature.pdfscanner.data.local.db.entity.ScannedPdfEntity

// TODO: Add labels to the database. The user might want to categorize the scanned PDFs.

const val CURRENT_VERSION = 1

@Database(
    entities = [ScannedPdfEntity::class],
    version = CURRENT_VERSION,
    exportSchema = true,
    autoMigrations = [],
)
abstract class PdfDatabase : RoomDatabase() {
    abstract fun scannedPdfDao(): ScannedPdfDao
}
