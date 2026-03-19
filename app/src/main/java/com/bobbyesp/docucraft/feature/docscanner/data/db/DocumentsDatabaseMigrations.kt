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
        """.trimIndent()
            )

            db.execSQL(
                """
            INSERT INTO `scanned_documents_fts`(`rowid`, `title`, `description`, `filename`) 
            SELECT `rowid`, `title`, `description`, `filename` FROM `scanned_documents`
        """.trimIndent()
            )
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. Create new table
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `scanned_documents_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                    `uuid` TEXT NOT NULL, 
                    `filename` TEXT NOT NULL, 
                    `title` TEXT, 
                    `description` TEXT, 
                    `path` TEXT NOT NULL, 
                    `createdTimestamp` INTEGER NOT NULL, 
                    `fileSize` INTEGER NOT NULL, 
                    `pageCount` INTEGER NOT NULL, 
                    `thumbnail` TEXT
                )
                """.trimIndent()
            )

            // 2. Migrate the data. The old 'id' (TEXT) became now 'uuid'
            db.execSQL(
                """
                INSERT INTO `scanned_documents_new` (
                    `uuid`, `filename`, `title`, `description`, `path`, 
                    `createdTimestamp`, `fileSize`, `pageCount`, `thumbnail`
                )
                SELECT 
                    `id`, `filename`, `title`, `description`, `path`, 
                    `createdTimestamp`, `fileSize`, `pageCount`, `thumbnail`
                FROM `scanned_documents`
                """.trimIndent()
            )

            // 3. Delete the old table and rename the new one
            db.execSQL("DROP TABLE `scanned_documents`")
            db.execSQL("ALTER TABLE `scanned_documents_new` RENAME TO `scanned_documents`")

            // 4. Recreate the FTS table with the new schema
            db.execSQL("DROP TABLE IF EXISTS `scanned_documents_fts`")
            db.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS `scanned_documents_fts` 
                USING FTS4(`title` TEXT, `description` TEXT, `filename` TEXT NOT NULL, content=`scanned_documents`)
                """.trimIndent()
            )

            // 5. Populate the new FTS table with the data from the old table
            db.execSQL(
                """
                INSERT INTO `scanned_documents_fts`(`docid`, `title`, `description`, `filename`)
                SELECT `id`, `title`, `description`, `filename`
                FROM `scanned_documents`
                """.trimIndent()
            )
        }
    }
}