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
 * The migration from the table of scans to the catalogue, on rows built from the exported schema of
 * version 4. People already have documents in that version, so what matters is that every one of
 * them arrives, and arrives usable.
 */
@RunWith(AndroidJUnit4::class)
class Migration4To5Test {

    @get:Rule val helper = migrationTestHelper()

    @After
    fun deleteDatabase() {
        deleteMigrationTestDatabase()
    }

    // The uuid is what navigation keys and the viewer's session memory refer to a document by.
    @Test
    fun everyDocumentKeepsItsRowIdAndItsUuid() {
        val ids = mutableListOf<Long>()
        val db = migrate {
            ids += insertVersion4Scan(uuid = "with-title", filename = "Scan_1", title = "Canción")
            ids += insertVersion4Scan(uuid = "without-title", filename = "Scan_2")
            ids += insertVersion4Scan(uuid = "spaces", filename = "Factura luz marzo")
            ids += insertVersion4Scan(uuid = "long", filename = "Scan_4", pageCount = 12)
        }

        assertEquals(
            listOf(
                "${ids[0]}|with-title",
                "${ids[1]}|without-title",
                "${ids[2]}|spaces",
                "${ids[3]}|long",
            ),
            db.rows("SELECT id, uuid FROM documents ORDER BY id"),
        )
    }

    @Test
    fun aScanBecomesAManagedDocumentFromTheScanner() {
        val db = migrate { insertVersion4Scan(uuid = "a", filename = "Scan_1") }

        assertEquals(
            listOf("MANAGED|SCAN|application/pdf|0|0|0|null|null|null"),
            db.rows(
                "SELECT custody, origin, mime_type, is_encrypted, is_favorite, ocr_enabled, " +
                    "folder_id, trashed_at, uri FROM documents"
            ),
        )
    }

    @Test
    fun whatTheUserWroteAndWhatIsKnownOfTheFileAreKept() {
        val db = migrate {
            insertVersion4Scan(
                uuid = "a",
                filename = "Scan_20260919_142530",
                title = "Contrato de alquiler",
                description = "Firmado en marzo",
                createdTimestamp = 1_758_000_000_000,
                fileSize = 345_678,
                pageCount = 4,
            )
            insertVersion4Scan(uuid = "b", filename = "Scan_2")
        }

        assertEquals(
            listOf(
                "Scan_20260919_142530|Contrato de alquiler|Firmado en marzo|345678|4",
                "Scan_2|null|null|2048|1",
            ),
            db.rows(
                "SELECT original_name, title, description, size_bytes, page_count " +
                    "FROM documents ORDER BY id"
            ),
        )
    }

    // Version 4 had one instant, the capture. It is also the best known answer to when the document
    // entered the catalogue and when it last changed.
    @Test
    fun theCaptureTimeFillsEveryInstant() {
        val db = migrate {
            insertVersion4Scan(
                uuid = "a",
                filename = "Scan_1",
                createdTimestamp = 1_758_000_000_000,
            )
        }

        assertEquals(
            listOf("1758000000000|1758000000000|1758000000000|1758000000000"),
            db.rows(
                "SELECT captured_at, created_at, updated_at, content_updated_at FROM documents"
            ),
        )
    }

    @Test
    fun theProviderUriBecomesAPathUnderTheFilesDirectory() {
        val db = migrate {
            insertVersion4Scan(uuid = "plain", filename = "Scan_20260919_142530")
            insertVersion4Scan(
                uuid = "spaces",
                filename = "Factura luz marzo",
                path = version4Location("Factura%20luz%20marzo.pdf"),
            )
            insertVersion4Scan(
                uuid = "debug",
                filename = "Scan_3",
                path =
                    "content://com.bobbyesp.docucraft.debug.fileprovider/scanned-pdfs/Scan_3.pdf",
            )
        }

        assertEquals(
            listOf(
                "scans/pdf/Scan_20260919_142530.pdf",
                "scans/pdf/Factura luz marzo.pdf",
                "scans/pdf/Scan_3.pdf",
            ),
            db.strings("SELECT file_path FROM documents ORDER BY id"),
        )
    }

    @Test
    fun eachDocumentGetsAPageForEachOfItsPagesStillToBeRead() {
        val db = migrate {
            insertVersion4Scan(uuid = "one", filename = "Scan_1", pageCount = 1)
            insertVersion4Scan(uuid = "three", filename = "Scan_2", pageCount = 3)
        }

        assertEquals(
            listOf(
                "one|0|PENDING|0",
                "three|0|PENDING|0",
                "three|1|PENDING|0",
                "three|2|PENDING|0",
            ),
            db.rows(
                "SELECT d.uuid, p.page_index, p.text_status, p.attempts " +
                    "FROM pages p JOIN documents d ON d.id = p.document_id " +
                    "ORDER BY d.id, p.page_index"
            ),
        )
    }

    // A scanner that did not report its pages was recorded as 0. Kept as 0, the document could
    // never be renamed or moved again: a managed document with no pages is what the trigger
    // rejects.
    @Test
    fun aDocumentRecordedWithoutPagesHasAnUnknownPageCountAndCanStillBeChanged() {
        val db = migrate { insertVersion4Scan(uuid = "a", filename = "Scan_1", pageCount = 0) }

        assertEquals(listOf("null"), db.rows("SELECT page_count FROM documents"))
        assertEquals(0, db.long("SELECT COUNT(*) FROM pages"))

        db.execSQL("UPDATE documents SET title = 'Renamed' WHERE uuid = 'a'")
        assertEquals(listOf("Renamed"), db.strings("SELECT title FROM documents"))
    }

    // Version 4 named a file after its scan and overwrote one of the same name, so two rows can
    // point at one file. Its content is the later scan's.
    @Test
    fun twoDocumentsThatSharedAFileBothSurviveAndTheLaterOneKeepsIt() {
        val db = migrate {
            insertVersion4Scan(uuid = "earlier", filename = "Scan_1", title = "First")
            insertVersion4Scan(uuid = "later", filename = "Scan_1", title = "Second")
            insertVersion4Scan(uuid = "other", filename = "Scan_2")
        }

        assertEquals(
            listOf(
                "earlier|First|missing/earlier.pdf|NOT_FOUND",
                "later|Second|scans/pdf/Scan_1.pdf|UNKNOWN",
                "other|null|scans/pdf/Scan_2.pdf|UNKNOWN",
            ),
            db.rows(
                "SELECT d.uuid, d.title, d.file_path, a.availability " +
                    "FROM documents d JOIN document_activity a ON a.document_id = d.id " +
                    "ORDER BY d.id"
            ),
        )
    }

    // Recents is ordered by the last activity, and a document never opened still has to be there.
    @Test
    fun eachDocumentGetsItsActivityNeverOpenedAndActiveSinceItWasScanned() {
        val db = migrate {
            insertVersion4Scan(
                uuid = "a",
                filename = "Scan_1",
                createdTimestamp = 1_758_000_000_000,
            )
        }

        assertEquals(
            listOf("null|1758000000000|null|null|UNKNOWN|null"),
            db.rows(
                "SELECT last_opened_at, last_activity_at, reading_page, reading_offset, " +
                    "availability, availability_checked_at FROM document_activity"
            ),
        )
    }

    // The index of version 4 kept accents, so "cancion" did not find "Canción". The new one is
    // built from the migrated rows, and has to find them without anything being edited first.
    @Test
    fun searchFindsTheMigratedDocumentsIgnoringAccentsAndCase() {
        val db = migrate {
            insertVersion4Scan(uuid = "song", filename = "Scan_1", title = "Canción de cuna")
            insertVersion4Scan(uuid = "bill", filename = "Factura luz marzo")
            insertVersion4Scan(uuid = "child", filename = "Scan_3", description = "El niño pequeño")
        }

        assertEquals(listOf("song"), db.search("cancion*"))
        assertEquals(listOf("song"), db.search("CANCIÓN*"))
        assertEquals(listOf("bill"), db.search("factura* luz*"))
        assertEquals(listOf("child"), db.search("nino*"))
        assertEquals(emptyList<String>(), db.search("factura* gas*"))
    }

    @Test
    fun theTablesOfVersion4AreGoneWithTheirTriggers() {
        val db = migrate { insertVersion4Scan(uuid = "a", filename = "Scan_1") }

        assertEquals(
            emptyList<String>(),
            db.strings("SELECT name FROM sqlite_master WHERE name LIKE '%scanned_documents%'"),
        )
    }

    @Test
    fun theRulesOfTheCatalogueAreInPlace() {
        val db = migrate { insertVersion4Scan(uuid = "a", filename = "Scan_1") }

        assertEquals(
            listOf(
                "document_tags_managed_only",
                "documents_custody_insert",
                "documents_custody_update",
                "folders_rules_insert",
                "folders_rules_update",
            ),
            db.strings(
                "SELECT name FROM sqlite_master " +
                    "WHERE type = 'trigger' AND name NOT LIKE 'room_fts_content_sync_%' " +
                    "ORDER BY name"
            ),
        )
    }

    @Test
    fun nothingRefersToARowThatIsNotThere() {
        val db = migrate {
            insertVersion4Scan(uuid = "a", filename = "Scan_1", pageCount = 5)
            insertVersion4Scan(uuid = "b", filename = "Scan_2", pageCount = 0)
        }

        assertEquals(emptyList<String>(), db.rows("PRAGMA foreign_key_check"))
    }

    @Test
    fun anEmptyCatalogueMigrates() {
        val db = migrate {}

        assertEquals(0, db.long("SELECT COUNT(*) FROM documents"))
    }

    private fun migrate(insertScans: SupportSQLiteDatabase.() -> Unit): SupportSQLiteDatabase {
        helper.createDatabase(MigrationTestDatabase, 4).use { it.insertScans() }
        return helper.runMigrationsAndValidate(
            MigrationTestDatabase,
            5,
            true,
            DocumentsDatabaseMigrations.MIGRATION_4_5,
        )
    }

    /** The uuids of the documents the full-text index finds for [match], in the order saved. */
    private fun SupportSQLiteDatabase.search(match: String): List<String?> =
        query(
                "SELECT d.uuid FROM documents_fts JOIN documents d ON d.id = documents_fts.rowid " +
                    "WHERE documents_fts MATCH ? ORDER BY d.id",
                arrayOf(match),
            )
            .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
}
