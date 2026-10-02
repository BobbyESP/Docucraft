/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A catalogue created by an old version of the app reaches the current schema with its documents.
 * Someone who has not updated in a long time goes through every migration in one start.
 *
 * Nothing starts at version 3: its schema was never exported, and the helper builds a version from
 * its export.
 */
@RunWith(AndroidJUnit4::class)
class MigrationChainTest {

    @get:Rule val helper = migrationTestHelper()

    @After
    fun deleteDatabase() {
        deleteMigrationTestDatabase()
    }

    @Test
    fun aCatalogueFromVersion1KeepsItsDocuments() {
        helper.createDatabase(MigrationTestDatabase, 1).use { db ->
            db.insertLegacyDocument("scanned_pdfs", id = "first", filename = "Scan_1")
            db.insertLegacyDocument("scanned_pdfs", id = "second", filename = "Scan_2", title = "B")
        }

        assertEquals(listOf("first", "second"), migrate().identifiers())
    }

    @Test
    fun aCatalogueFromVersion2KeepsItsDocuments() {
        helper.createDatabase(MigrationTestDatabase, 2).use { db ->
            db.insertLegacyDocument("scanned_documents", id = "first", filename = "Scan_1")
            db.insertLegacyDocument("scanned_documents", id = "second", filename = "Scan_2")
        }

        assertEquals(listOf("first", "second"), migrate().identifiers())
    }

    // What was the text primary key up to version 3 is the uuid from version 4 on.
    private fun migrate() =
        helper.runMigrationsAndValidate(
            MigrationTestDatabase,
            CURRENT_VERSION,
            true,
            DocumentsDatabaseMigrations.MIGRATION_2_3,
            DocumentsDatabaseMigrations.MIGRATION_3_4,
        )

    private fun SupportSQLiteDatabase.identifiers() =
        strings("SELECT uuid FROM scanned_documents ORDER BY id")
}
