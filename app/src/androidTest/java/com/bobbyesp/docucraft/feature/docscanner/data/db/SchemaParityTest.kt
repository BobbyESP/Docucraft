/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A database that was migrated and one that was created are the same database.
 *
 * Room checks tables, indices and views after a migration, but not triggers, and the rules of the
 * catalogue are triggers. A migration that forgot one would leave old installs without a rule that
 * new installs have, and nothing would say so.
 *
 * Both databases are opened the way the app opens its own, through [DocumentsDatabase.builder].
 */
@RunWith(AndroidJUnit4::class)
class SchemaParityTest {

    @get:Rule val helper = migrationTestHelper()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun deleteDatabases() {
        deleteMigrationTestDatabase()
        context.deleteDatabase(NewDatabase)
    }

    @Test
    fun aMigratedDatabaseHasExactlyTheSchemaOfANewOne() {
        helper.createDatabase(MigrationTestDatabase, 4).use { db ->
            db.insertVersion4Scan(uuid = "a", filename = "Scan_1", pageCount = 2)
        }

        val migrated = schemaOf(MigrationTestDatabase)
        val created = schemaOf(NewDatabase)

        // Compared one object at a time first, so a difference names what differs.
        assertEquals(created.keys, migrated.keys)
        for ((name, sql) in created) assertEquals(name, sql, migrated[name])
        assertTrue("An empty schema proves nothing", created.size > 20)
    }

    @Test
    fun aCatalogueFromVersion1HasTheSchemaOfANewOneToo() {
        helper.createDatabase(MigrationTestDatabase, 1).use { db ->
            db.insertLegacyDocument("scanned_pdfs", id = "first", filename = "Scan_1")
        }

        assertEquals(schemaOf(NewDatabase), schemaOf(MigrationTestDatabase))
    }

    // Room turns foreign keys on when it opens a database that declares any. Without them, a
    // deleted document would leave its pages and its activity behind.
    @Test
    fun foreignKeysAreEnforcedAndNothingBreaksThemAfterMigrating() {
        helper.createDatabase(MigrationTestDatabase, 4).use { db ->
            db.insertVersion4Scan(uuid = "a", filename = "Scan_1", pageCount = 3)
            db.insertVersion4Scan(uuid = "b", filename = "Scan_1", pageCount = 0)
        }

        val database = DocumentsDatabase.builder(context, MigrationTestDatabase).build()
        try {
            val db = database.openHelper.writableDatabase
            assertEquals(1, db.long("PRAGMA foreign_keys"))
            assertEquals(emptyList<String>(), db.rows("PRAGMA foreign_key_check"))
        } finally {
            database.close()
        }
    }

    /** Every table, index, view and trigger of the database called [name], by type and name. */
    private fun schemaOf(name: String): Map<String, String> {
        val database = DocumentsDatabase.builder(context, name).build()
        try {
            return database.openHelper.writableDatabase
                .query("SELECT type, name, tbl_name, sql FROM sqlite_master ORDER BY type, name")
                .use { cursor ->
                    buildMap {
                        while (cursor.moveToNext()) {
                            val key = "${cursor.getString(0)} ${cursor.getString(1)}"
                            put(key, "on ${cursor.getString(2)}: ${cursor.getString(3)}")
                        }
                    }
                }
        } finally {
            database.close()
        }
    }

    private companion object {
        const val NewDatabase = "new-install-test.db"
    }
}
