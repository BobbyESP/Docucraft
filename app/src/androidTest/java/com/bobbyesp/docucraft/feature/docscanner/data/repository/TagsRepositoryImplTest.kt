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
import com.bobbyesp.docucraft.feature.docscanner.domain.model.NewScan
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.TagChange
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Tags against a real database, where the cascades and the unique names are. */
@RunWith(AndroidJUnit4::class)
class TagsRepositoryImplTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val database =
        Room.inMemoryDatabaseBuilder(context, DocumentsDatabase::class.java)
            .addCallback(DatabaseTriggers.CreateOnNewDatabase)
            .build()
    private val locations = DocumentLocations(context)
    private var clock = 1_000L
    private val tags = TagsRepositoryImpl(database, locations, now = { clock++ })
    private val documents = DocumentsRepositoryImpl(database.documentDao(), locations)
    private val db
        get() = database.openHelper.writableDatabase

    @After
    fun closeDatabase() {
        database.close()
    }

    // --- names ---

    // Typing "facturas " when "Facturas" exists is asking for that tag, not for a second one.
    @Test
    fun aNameThatOnlyDiffersInCaseAccentsOrSpacesIsTheSameTag() = runBlocking {
        val first = tags.getOrCreate("a", "Facturas")
        val again = tags.getOrCreate("b", "  fácturas ")

        assertEquals("a", first?.uuid)
        assertEquals(first, again)
        assertEquals(listOf("Facturas"), tags.observeTags().first().map { it.name })
    }

    @Test
    fun aTagNeedsAName() = runBlocking {
        assertNull(tags.getOrCreate("a", "   "))
        assertEquals(0, db.long("SELECT COUNT(*) FROM tags"))
    }

    @Test
    fun aTagCanBeRenamedToANameThatIsFreeAndToItsOwnWrittenDifferently() = runBlocking {
        tags.getOrCreate("a", "Facturas")
        tags.getOrCreate("b", "Garantías")

        assertEquals(TagChange.NameTaken, tags.rename("a", "garantias"))
        assertEquals(TagChange.NameEmpty, tags.rename("a", " "))
        assertEquals(TagChange.NotFound, tags.rename("gone", "Recibos"))
        assertEquals(TagChange.Done, tags.rename("a", "FACTURAS"))
        assertEquals(TagChange.Done, tags.rename("b", "Recibos"))
        assertEquals(listOf("FACTURAS", "Recibos"), tags.observeTags().first().map { it.name })
    }

    @Test
    fun aTagRemembersItsColour() = runBlocking {
        tags.getOrCreate("a", "Facturas")

        tags.setColor("a", "terracotta")

        assertEquals("terracotta", tags.observeTags().first().single().color)
    }

    // --- tagging ---

    @Test
    fun aDocumentCanHaveSeveralTagsAndATagSeveralDocuments() = runBlocking {
        document("one")
        document("two")
        tags.getOrCreate("bills", "Facturas")
        tags.getOrCreate("home", "Casa")

        tags.tag("one", "bills")
        tags.tag("one", "home")
        tags.tag("two", "bills")

        assertEquals(listOf("Casa", "Facturas"), tagsOf("one"))
        assertEquals(listOf("Facturas"), tagsOf("two"))

        tags.untag("one", "bills")
        assertEquals(listOf("Casa"), tagsOf("one"))
    }

    // When a tag was put on a document is kept. Putting it again must not move that.
    @Test
    fun taggingADocumentTwiceChangesNothing() = runBlocking {
        document("one")
        tags.getOrCreate("bills", "Facturas")

        tags.tag("one", "bills")
        val firstTime = db.rows("SELECT tagged_at FROM document_tags")
        tags.tag("one", "bills")

        assertEquals(firstTime, db.rows("SELECT tagged_at FROM document_tags"))
    }

    // Another app's document is not organized. Asked to tag one, the repository leaves it alone
    // rather than letting the database's rule answer with an exception.
    @Test
    fun anotherAppsDocumentIsNotTagged() = runBlocking {
        tags.getOrCreate("bills", "Facturas")
        db.execSQL(
            "INSERT INTO documents (uuid, custody, original_name, mime_type, is_encrypted, " +
                "is_favorite, ocr_enabled, uri, created_at, updated_at, content_updated_at) " +
                "VALUES ('linked', 'LINKED', 'x.pdf', 'application/pdf', 0, 0, 0, " +
                "'content://other.app/1', 5, 5, 5)"
        )

        tags.tag("linked", "bills")
        tags.tag("no-such-document", "bills")
        tags.tag("linked", "no-such-tag")

        assertEquals(0, db.long("SELECT COUNT(*) FROM document_tags"))
    }

    // --- finding by tags ---

    @Test
    fun documentsAreFoundByHavingEveryOneOfTheTagsAskedFor() = runBlocking {
        document("both")
        document("bills-only")
        document("neither")
        tags.getOrCreate("bills", "Facturas")
        tags.getOrCreate("home", "Casa")
        tags.tag("both", "bills")
        tags.tag("both", "home")
        tags.tag("bills-only", "bills")

        assertEquals(setOf("both", "bills-only"), withAll("bills").toSet())
        assertEquals(listOf("both"), withAll("bills", "home"))
        // The same tag asked for twice is asked for once.
        assertEquals(listOf("both"), withAll("home", "home"))
        assertEquals(emptyList<String>(), withAll())
    }

    @Test
    fun aDocumentInTheBinIsNotFoundByItsTags() = runBlocking {
        document("kept")
        document("binned")
        tags.getOrCreate("bills", "Facturas")
        tags.tag("kept", "bills")
        tags.tag("binned", "bills")
        db.execSQL("UPDATE documents SET trashed_at = 1 WHERE uuid = 'binned'")

        assertEquals(listOf("kept"), withAll("bills"))
        // It keeps its tags all the same, for when it is restored.
        assertEquals(listOf("Facturas"), tagsOf("binned"))
    }

    // --- deleting ---

    @Test
    fun deletingATagTakesItOffItsDocumentsAndLeavesThem() = runBlocking {
        document("one")
        tags.getOrCreate("bills", "Facturas")
        tags.getOrCreate("home", "Casa")
        tags.tag("one", "bills")
        tags.tag("one", "home")

        tags.delete("bills")

        assertEquals(listOf("Casa"), tagsOf("one"))
        assertEquals(1, db.long("SELECT COUNT(*) FROM documents"))
    }

    @Test
    fun deletingADocumentLeavesItsTagsForTheOthers() = runBlocking {
        document("one")
        tags.getOrCreate("bills", "Facturas")
        tags.tag("one", "bills")

        documents.deleteDocument("one")

        assertEquals(0, db.long("SELECT COUNT(*) FROM document_tags"))
        assertEquals(listOf("Facturas"), tags.observeTags().first().map { it.name })
    }

    // --- sections in Home ---

    // Written at once: no two tags are ever left with the same place.
    @Test
    fun theTagsChosenForHomeHaveTheOrderTheyWereGivenAndTheRestHaveNone() = runBlocking {
        tags.getOrCreate("a", "A")
        tags.getOrCreate("b", "B")
        tags.getOrCreate("c", "C")

        tags.setHomeSections(listOf("c", "a"))
        assertEquals(listOf("a|1", "b|null", "c|0"), positions())

        tags.setHomeSections(listOf("b"))
        assertEquals(listOf("a|null", "b|0", "c|null"), positions())

        tags.setHomeSections(emptyList())
        assertEquals(listOf("a|null", "b|null", "c|null"), positions())
    }

    private suspend fun tagsOf(document: String): List<String> =
        tags.observeTagsOf(document).first().map { it.name }

    private suspend fun withAll(vararg tagUuids: String): List<String> =
        tags.observeDocumentsWithAll(tagUuids.toList()).first().map { it.uuid }

    private fun positions(): List<String> =
        db.rows("SELECT uuid, home_position FROM tags ORDER BY uuid")

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
