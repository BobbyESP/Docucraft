package com.bobbyesp.docucraft.feature.docscanner.data.db

import androidx.room.RenameTable
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DocumentsDatabaseMigrations {
    /**
     * Migration from version 1 to version 2.
     * This migration renames the table from "scanned_pdfs" to "scanned_documents".
     */
    @RenameTable(fromTableName = "scanned_pdfs", toTableName = "scanned_documents")
    class Migration1To2 : AutoMigrationSpec

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
            CREATE VIRTUAL TABLE IF NOT EXISTS `scanned_documents_fts` 
            USING FTS4(`title`, `description`, `filename`, content=`scanned_documents`)
        """
            )

            db.execSQL(
                """
            INSERT INTO `scanned_documents_fts`(`rowid`, `title`, `description`, `filename`) 
            SELECT `rowid`, `title`, `description`, `filename` FROM `scanned_documents`
        """
            )
        }
    }
}