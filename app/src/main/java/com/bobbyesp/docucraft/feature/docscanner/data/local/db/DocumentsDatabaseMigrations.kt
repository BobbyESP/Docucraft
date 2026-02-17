package com.bobbyesp.docucraft.feature.docscanner.data.local.db

import androidx.room.RenameTable
import androidx.room.migration.AutoMigrationSpec

object DocumentsDatabaseMigrations {
    /**
     * Migration from version 1 to version 2.
     * This migration renames the table from "scanned_pdfs" to "scanned_documents".
     */
    @RenameTable(fromTableName = "scanned_pdfs", toTableName = "scanned_documents")
    class Migration1To2 : AutoMigrationSpec
}