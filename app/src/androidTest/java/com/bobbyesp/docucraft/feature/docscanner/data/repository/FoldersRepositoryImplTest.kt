/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bobbyesp.docucraft.feature.docscanner.data.db.DatabaseTriggers
import com.bobbyesp.docucraft.feature.docscanner.data.db.DocumentsDatabase
import com.bobbyesp.docucraft.feature.docscanner.data.db.long
import com.bobbyesp.docucraft.feature.docscanner.data.db.rows
import com.bobbyesp.docucraft.feature.docscanner.data.storage.DocumentLocations
import com.bobbyesp.docucraft.feature.docscanner.domain.SortOption
import com.bobbyesp.docucraft.feature.docscanner.domain.model.NewScan
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.FolderChange
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Folders against a real database: the rules are kept by the repository, the triggers and the
 * indices together, and what matters is what they do between them.
 */
@RunWith(AndroidJUnit4::class)
class FoldersRepositoryImplTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val database =
        Room.inMemoryDatabaseBuilder(context, DocumentsDatabase::class.java)
            .addCallback(DatabaseTriggers.CreateOnNewDatabase)
            .build()
    private val locations = DocumentLocations(context)
    private var clock = 1_000L
    private val folders = FoldersRepositoryImpl(database, locations, now = { clock++ })
    private val documents = DocumentsRepositoryImpl(database.documentDao(), locations)
    private val db
        get() = database.openHelper.writableDatabase

    @After
    fun closeDatabase() {
        database.close()
    }

    // --- creating and naming ---

    @Test
    fun foldersAreListedByNameUnderTheirParentOrInTheRoot() = runBlocking {
        folders.create("b", "Facturas", parentUuid = null)
        folders.create("a", "contratos", parentUuid = null)
        folders.create("c", "2026", parentUuid = "b")

        assertEquals(listOf("contratos", "Facturas"), names(parent = null))
        assertEquals(listOf("2026"), names(parent = "b"))
        assertEquals("b", folders.getFolder("c")?.parentUuid)
        assertNull(folders.getFolder("b")?.parentUuid)
    }

    // "Facturas" and "facturas " are the same folder to whoever is looking for it.
    @Test
    fun twoFoldersWithTheSameParentCannotHaveTheSameNameHoweverItIsWritten() = runBlocking {
        folders.create("a", "Facturas", parentUuid = null)
        folders.create("p", "Casa", parentUuid = null)
        folders.create("b", "Facturas", parentUuid = "p")

        assertEquals(FolderChange.NameTaken, folders.create("x", " fácturas ", parentUuid = null))
        assertEquals(FolderChange.NameTaken, folders.create("y", "FACTURAS", parentUuid = "p"))
        // The same name is free under another parent.
        assertEquals(FolderChange.Done, folders.create("z", "Facturas", parentUuid = "a"))
        assertEquals(4, db.long("SELECT COUNT(*) FROM folders"))
    }

    @Test
    fun aFolderNeedsANameAndAParentThatExists() = runBlocking {
        assertEquals(FolderChange.NameEmpty, folders.create("a", "   ", parentUuid = null))
        assertEquals(FolderChange.NotFound, folders.create("a", "Facturas", parentUuid = "gone"))
        assertEquals(0, db.long("SELECT COUNT(*) FROM folders"))
    }

    @Test
    fun aNameIsKeptAsWrittenWithoutStraySpaces() = runBlocking {
        folders.create("a", "  Facturas   de la Luz ", parentUuid = null)

        assertEquals("Facturas de la Luz", folders.getFolder("a")?.name)
    }

    @Test
    fun aFolderCanBeRenamedToANameThatIsFreeAndToItsOwnWrittenDifferently() = runBlocking {
        folders.create("a", "Facturas", parentUuid = null)
        folders.create("b", "Contratos", parentUuid = null)

        assertEquals(FolderChange.NameTaken, folders.rename("a", "contratos"))
        assertEquals(FolderChange.NameEmpty, folders.rename("a", " "))
        assertEquals(FolderChange.NotFound, folders.rename("gone", "Recibos"))
        assertEquals(FolderChange.Done, folders.rename("a", "FACTURAS"))
        assertEquals(FolderChange.Done, folders.rename("b", "Recibos"))
        assertEquals(listOf("FACTURAS", "Recibos"), names(parent = null))
    }

    // --- moving ---

    @Test
    fun aFolderCanBeMovedIntoAnotherAndBackToTheRoot() = runBlocking {
        folders.create("a", "Facturas", parentUuid = null)
        folders.create("b", "Casa", parentUuid = null)

        assertEquals(FolderChange.Done, folders.move("a", parentUuid = "b"))
        assertEquals(listOf("Casa", "Facturas"), folders.pathTo("a").map { it.name })

        assertEquals(FolderChange.Done, folders.move("a", parentUuid = null))
        assertEquals(listOf("Facturas"), folders.pathTo("a").map { it.name })
    }

    // The trigger only sees a folder that is its own parent. A longer loop is the repository's to
    // stop, because the oldest SQLite the app runs on cannot walk the ancestors in a trigger.
    @Test
    fun aFolderCannotBeMovedIntoItselfOrIntoAFolderInsideIt() = runBlocking {
        folders.create("a", "A", parentUuid = null)
        folders.create("b", "B", parentUuid = "a")
        folders.create("c", "C", parentUuid = "b")

        assertEquals(FolderChange.WouldContainItself, folders.move("a", parentUuid = "a"))
        assertEquals(FolderChange.WouldContainItself, folders.move("a", parentUuid = "c"))
        assertEquals(FolderChange.WouldContainItself, folders.move("b", parentUuid = "c"))
        assertEquals(listOf("A", "B", "C"), folders.pathTo("c").map { it.name })
    }

    @Test
    fun aFolderCannotBeMovedWhereItsNameIsTakenOrToAParentThatIsGone() = runBlocking {
        folders.create("a", "Facturas", parentUuid = null)
        folders.create("p", "Casa", parentUuid = null)
        folders.create("b", "facturas", parentUuid = "p")

        assertEquals(FolderChange.NameTaken, folders.move("b", parentUuid = null))
        assertEquals(FolderChange.NameTaken, folders.move("a", parentUuid = "p"))
        assertEquals(FolderChange.NotFound, folders.move("a", parentUuid = "gone"))
        assertEquals(FolderChange.NotFound, folders.move("gone", parentUuid = null))
    }

    // --- what a folder remembers ---

    @Test
    fun pinnedFoldersAreListedInTheOrderTheyWerePinned() = runBlocking {
        folders.create("a", "A", parentUuid = null)
        folders.create("b", "B", parentUuid = null)
        folders.create("c", "C", parentUuid = "a")

        folders.setPinned("c", true)
        folders.setPinned("a", true)
        // Pinning again what is pinned does not send it to the end.
        folders.setPinned("c", true)

        assertEquals(listOf("C", "A"), folders.observePinned().first().map { it.name })

        folders.setPinned("c", false)
        assertEquals(listOf("A"), folders.observePinned().first().map { it.name })
    }

    @Test
    fun aFolderRemembersItsColourItsIconAndHowItIsSorted() = runBlocking {
        folders.create("a", "A", parentUuid = null)

        folders.setAppearance("a", color = "terracotta", icon = "receipt_long")
        folders.setSort("a", SortOption.NameAsc)

        val folder = checkNotNull(folders.getFolder("a"))
        assertEquals("terracotta", folder.color)
        assertEquals("receipt_long", folder.icon)
        assertEquals(SortOption.NameAsc, folder.sort)

        folders.setSort("a", null)
        assertNull(folders.getFolder("a")?.sort)
    }

    // --- documents in folders ---

    @Test
    fun aDocumentIsInOneFolderOrInTheRoot() = runBlocking {
        folders.create("a", "A", parentUuid = null)
        folders.create("b", "B", parentUuid = null)
        document("one")
        document("two")

        assertEquals(FolderChange.Done, folders.moveDocuments(listOf("one", "two"), "a"))
        assertEquals(FolderChange.Done, folders.moveDocuments(listOf("two"), "b"))

        assertEquals(listOf("one"), documentsIn("a"))
        assertEquals(listOf("two"), documentsIn("b"))
        assertEquals(emptyList<String>(), documentsIn(null))

        assertEquals(FolderChange.Done, folders.moveDocuments(listOf("one"), null))
        assertEquals(listOf("one"), documentsIn(null))
        assertEquals(FolderChange.NotFound, folders.moveDocuments(listOf("one"), "gone"))
    }

    @Test
    fun aFolderListsTheLibraryNotTheBin() = runBlocking {
        folders.create("a", "A", parentUuid = null)
        document("kept")
        document("binned")
        folders.moveDocuments(listOf("kept", "binned"), "a")
        db.execSQL("UPDATE documents SET trashed_at = 1 WHERE uuid = 'binned'")

        assertEquals(listOf("kept"), documentsIn("a"))
    }

    // Another app's document only appears in Recents. Asked to file one, the repository leaves it
    // where it is rather than letting the database refuse the whole move.
    @Test
    fun anotherAppsDocumentIsNotFiled() = runBlocking {
        folders.create("a", "A", parentUuid = null)
        document("mine")
        db.execSQL(
            "INSERT INTO documents (uuid, custody, original_name, mime_type, is_encrypted, " +
                "is_favorite, ocr_enabled, uri, created_at, updated_at, content_updated_at) " +
                "VALUES ('linked', 'LINKED', 'x.pdf', 'application/pdf', 0, 0, 0, " +
                "'content://other.app/1', 5, 5, 5)"
        )

        assertEquals(FolderChange.Done, folders.moveDocuments(listOf("mine", "linked"), "a"))

        assertEquals(listOf("mine"), documentsIn("a"))
        assertEquals(
            listOf("null"),
            db.rows("SELECT folder_id FROM documents WHERE uuid = 'linked'"),
        )
    }

    // --- deleting a folder ---

    // Deleting a folder never deletes a document, not even one already in the bin.
    @Test
    fun deletingAFolderSendsItsDocumentsToTheFolderItWasIn() = runBlocking {
        folders.create("p", "Casa", parentUuid = null)
        folders.create("a", "Facturas", parentUuid = "p")
        document("kept")
        document("binned")
        folders.moveDocuments(listOf("kept", "binned"), "a")
        db.execSQL("UPDATE documents SET trashed_at = 1 WHERE uuid = 'binned'")

        folders.delete("a")

        assertNull(folders.getFolder("a"))
        assertEquals(listOf("kept"), documentsIn("p"))
        assertEquals(
            listOf("binned|p", "kept|p"),
            db.rows(
                "SELECT d.uuid, f.uuid FROM documents d JOIN folders f ON f.id = d.folder_id " +
                    "ORDER BY d.uuid"
            ),
        )
    }

    // The other way to delete a folder, asked for by name: what it holds goes to the bin, and
    // comes back, if restored, to the folder the deleted one was in.
    @Test
    fun deletingAFolderWithItsContentsSendsEveryDocumentUnderItToTheBin() = runBlocking {
        folders.create("p", "Casa", parentUuid = null)
        folders.create("a", "Facturas", parentUuid = "p")
        folders.create("b", "2026", parentUuid = "a")
        document("beside")
        document("in")
        document("deep")
        folders.moveDocuments(listOf("beside"), "p")
        folders.moveDocuments(listOf("in"), "a")
        folders.moveDocuments(listOf("deep"), "b")

        folders.deleteWithContents("a")

        assertNull(folders.getFolder("a"))
        assertNull(folders.getFolder("b"))
        assertEquals(listOf("beside"), documentsIn("p"))
        assertEquals(
            listOf("beside|p|0", "deep|p|1", "in|p|1"),
            db.rows(
                "SELECT d.uuid, f.uuid, d.trashed_at IS NOT NULL FROM documents d " +
                    "JOIN folders f ON f.id = d.folder_id ORDER BY d.uuid"
            ),
        )
    }

    @Test
    fun deletingAFolderInTheRootSendsItsContentsToTheRoot() = runBlocking {
        folders.create("a", "Facturas", parentUuid = null)
        folders.create("b", "2026", parentUuid = "a")
        document("one")
        folders.moveDocuments(listOf("one"), "a")

        folders.delete("a")

        assertEquals(listOf("2026"), names(parent = null))
        assertEquals(listOf("one"), documentsIn(null))
    }

    // A subfolder that goes up may land beside a folder with its name. Refusing would leave the
    // user unable to delete; merging would mix two folders nobody asked to mix.
    @Test
    fun aSubfolderThatLandsWhereItsNameIsTakenIsRenamed() = runBlocking {
        folders.create("p", "Casa", parentUuid = null)
        folders.create("taken", "2026", parentUuid = "p")
        folders.create("taken2", "2026 (2)", parentUuid = "p")
        folders.create("a", "Facturas", parentUuid = "p")
        folders.create("up", "2026", parentUuid = "a")
        folders.create("free", "Luz", parentUuid = "a")

        folders.delete("a")

        assertEquals(listOf("2026", "2026 (2)", "2026 (3)", "Luz"), names(parent = "p"))
        assertEquals("2026 (3)", folders.getFolder("up")?.name)
    }

    // The folder being deleted still holds its name while its contents leave. A subfolder called
    // the same takes its place, and keeps the name.
    @Test
    fun aSubfolderWithTheNameOfTheDeletedFolderTakesItsPlace() = runBlocking {
        folders.create("outer", "Facturas", parentUuid = null)
        folders.create("inner", "Facturas", parentUuid = "outer")

        folders.delete("outer")

        assertEquals(listOf("Facturas"), names(parent = null))
        assertEquals("inner", folders.observeFolders(null).first().single().uuid)
    }

    @Test
    fun deletingAFolderThatIsNotThereChangesNothing() = runBlocking {
        folders.create("a", "A", parentUuid = null)

        folders.delete("gone")

        assertEquals(listOf("A"), names(parent = null))
    }

    private suspend fun names(parent: String?): List<String> =
        folders.observeFolders(parent).first().map { it.name }

    private suspend fun documentsIn(folder: String?): List<String> =
        folders.observeDocuments(folder).first().map { it.uuid }

    private suspend fun document(uuid: String) {
        documents.addScan(
            NewScan(
                uuid = uuid,
                originalName = "Scan_$uuid",
                filePath = "documents/$uuid.pdf",
                sizeBytes = 1,
                contentHash = "hash-$uuid",
                pageCount = 1,
                capturedAtEpochMillis = 1,
            )
        )
    }
}
