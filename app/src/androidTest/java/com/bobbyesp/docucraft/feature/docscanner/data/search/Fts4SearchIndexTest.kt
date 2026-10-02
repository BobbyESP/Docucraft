/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.search

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bobbyesp.docucraft.feature.docscanner.data.db.DatabaseTriggers
import com.bobbyesp.docucraft.feature.docscanner.data.db.DocumentsDatabase
import com.bobbyesp.docucraft.feature.docscanner.data.repository.DocumentsRepositoryImpl
import com.bobbyesp.docucraft.feature.docscanner.data.storage.DocumentLocations
import com.bobbyesp.docucraft.feature.docscanner.domain.model.NewScan
import com.bobbyesp.docucraft.feature.docscanner.domain.search.SearchHit
import java.text.Normalizer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Search against the device's own SQLite. What a query matches, what `matchinfo` and `snippet`
 * return and how the tokenizer folds a letter are all decided there, and the oldest SQLite the app
 * runs on is far behind a desk's.
 */
@RunWith(AndroidJUnit4::class)
class Fts4SearchIndexTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val database =
        Room.inMemoryDatabaseBuilder(context, DocumentsDatabase::class.java)
            .addCallback(DatabaseTriggers.CreateOnNewDatabase)
            .build()
    private val repository =
        DocumentsRepositoryImpl(database.documentDao(), DocumentLocations(context))
    private val index = Fts4SearchIndex(database.searchDao())
    private val db
        get() = database.openHelper.writableDatabase

    @After
    fun closeDatabase() {
        database.close()
    }

    // --- what matches ---

    // Android's SQLite reads `AND` as a word to look for, not as an operator: terms joined with it
    // only matched documents that also contained "and".
    @Test
    fun everyWordHasToMatchInAnyOrder() = runBlocking {
        document("luz", title = "Factura luz marzo")
        document("agua", title = "Factura agua marzo")

        assertEquals(listOf("luz"), search("factura luz"))
        assertEquals(listOf("luz"), search("marzo luz factura"))
        assertEquals(emptyList<String>(), search("factura gas"))
        assertEquals(setOf("luz", "agua"), search("factura marzo").toSet())
    }

    @Test
    fun eachWordMatchesTheBeginningOfAWordAndNotItsMiddle() = runBlocking {
        document("luz", title = "Factura luz marzo")

        assertEquals(listOf("luz"), search("fac lu"))
        assertEquals(emptyList<String>(), search("actura"))
    }

    // The index folds accents and case, whichever side has them: what is typed or what is stored.
    @Test
    fun accentsAndCaseMakeNoDifference() = runBlocking {
        document("song", title = "Canción de cuna")
        document("child", title = "Recibo", description = "Del niño")

        assertEquals(listOf("song"), search("cancion"))
        assertEquals(listOf("song"), search("CANCIÓN"))
        assertEquals(listOf("song"), search("Canción"))
        assertEquals(listOf("child"), search("nino"))
        assertEquals(listOf("child"), search("Niño"))
    }

    // The tokenizer leaves a letter with two marks as it is. A query stripped of its accents by
    // other rules would no longer match the very word it was copied from.
    @Test
    fun aWordIsFoundByTypingItExactlyAsItIsWritten() = runBlocking {
        document("vi", title = "Một tài liệu")
        document("ru", title = "Договор аренды")

        assertEquals(listOf("vi"), search("Một tài liệu"))
        assertEquals(listOf("vi"), search(Normalizer.normalize("Một", Normalizer.Form.NFD)))
        assertEquals(listOf("ru"), search("договор"))
    }

    // What the user types is text. Left as typed, these would open a phrase, exclude a word, pick
    // a column or fail to parse.
    @Test
    fun nothingTypedIsTakenForSyntax() = runBlocking {
        document("luz", title = "Factura luz marzo")
        document("gas", title = "Factura gas abril")

        assertEquals(listOf("luz"), search("Factura \"luz\" – Marzo*"))
        assertEquals(listOf("luz"), search("\"factura luz"))
        assertEquals(listOf("luz"), search("(factura) luz:"))
        // A dash excludes nothing: both words are required.
        assertEquals(listOf("luz"), search("luz -marzo"))
        // OR is one more word to find, and no document has it.
        assertEquals(emptyList<String>(), search("luz OR gas"))
        assertEquals(emptyList<String>(), search("luz NOT gas"))
    }

    @Test
    fun aQueryWithNothingToSearchForFindsNothing() = runBlocking {
        document("luz", title = "Factura luz marzo")

        for (query in listOf("", "   ", "***", "\"\"", "— – -", "()")) {
            assertEquals("for <$query>", emptyList<String>(), search(query))
        }
    }

    @Test
    fun onlyTheFirstEightWordsOfAQueryCount() = runBlocking {
        document("a", title = "uno dos tres cuatro cinco seis siete ocho")

        assertEquals(
            listOf("a"),
            search("uno dos tres cuatro cinco seis siete ocho nueve diez"),
        )
    }

    @Test
    fun everythingADocumentIsCalledAndDescribedAsIsSearched() = runBlocking {
        document(
            "a",
            originalName = "Scan_20260919_142530",
            title = "Contrato",
            description = "Piso",
        )
        db.execSQL(
            "UPDATE documents SET suggested_title = 'Arrendamiento', pdf_author = 'Notaría', " +
                "pdf_subject = 'Vivienda', pdf_keywords = 'fianza' WHERE uuid = 'a'"
        )

        for (word in
            listOf("scan", "contrato", "piso", "arrendamiento", "notaria", "vivienda", "fianza")) {
            assertEquals("for <$word>", listOf("a"), search(word))
        }
    }

    // The index follows the table: the old title stops matching as soon as it is replaced.
    @Test
    fun aRenamedDocumentIsFoundByItsNewTitleOnly() = runBlocking {
        document("a", title = "Contrato")
        repository.modifyFields("a", title = "Factura", description = null)

        assertEquals(emptyList<String>(), search("contrato"))
        assertEquals(listOf("a"), search("factura"))
    }

    // --- what is left out ---

    @Test
    fun theBinAndOtherAppsDocumentsAreNotSearched() = runBlocking {
        document("kept", title = "Factura luz", pages = listOf("consumo de marzo"))
        document("binned", title = "Factura gas", pages = listOf("consumo de abril"))
        db.execSQL("UPDATE documents SET trashed_at = 1 WHERE uuid = 'binned'")
        db.execSQL(
            "INSERT INTO documents (uuid, custody, original_name, mime_type, is_encrypted, " +
                "is_favorite, ocr_enabled, uri, created_at, updated_at, content_updated_at) " +
                "VALUES ('linked', 'LINKED', 'Factura agua.pdf', 'application/pdf', 0, 0, 0, " +
                "'content://other.app/1', 5, 5, 5)"
        )
        db.execSQL(
            "INSERT INTO document_activity (document_id, last_activity_at, availability) " +
                "SELECT id, 5, 'AVAILABLE' FROM documents WHERE uuid = 'linked'"
        )

        assertEquals(listOf("kept"), search("factura"))
        assertEquals(listOf("kept"), search("consumo"))
    }

    // --- in what order ---

    // A word in what a document is called says more about it than the same word in its
    // description, and either says more than the word appearing somewhere in its text.
    @Test
    fun aMatchInTheTitleOutranksOneInTheDescriptionWhichOutranksOneInAPage() = runBlocking {
        document("in-page", title = "Escritura", pages = listOf("Este contrato se firma en Madrid"))
        document("in-description", title = "Piso", description = "Contrato de alquiler")
        document("in-title", title = "Contrato")

        assertEquals(listOf("in-title", "in-description", "in-page"), search("contrato"))
    }

    @Test
    fun aDocumentFoundInItsNameAndInItsTextIsOneResultAboveOneFoundOnlyByName() = runBlocking {
        document("name-only", title = "Factura")
        document("both", title = "Factura", pages = listOf("Importe de la factura: 42,10 €"))

        assertEquals(listOf("both", "name-only"), search("factura"))
    }

    // Recents answers "the one I was just using", which is the likelier one to be looked for.
    @Test
    fun betweenEqualMatchesTheOneUsedMostRecentlyComesFirst() = runBlocking {
        document("older", title = "Recibo")
        document("newer", title = "Recibo")
        document("oldest", title = "Recibo")
        usedAt("older", 2_000)
        usedAt("newer", 3_000)
        usedAt("oldest", 1_000)

        assertEquals(listOf("newer", "older", "oldest"), search("recibo"))
    }

    // --- where in the text ---

    @Test
    fun aMatchInThePagesSaysOnWhichPageAndShowsTheWordsAroundIt() = runBlocking {
        document(
            "a",
            title = "Contrato",
            pages =
                listOf(
                    "Primera página sin nada que ver",
                    "Segunda página, tampoco",
                    "Las partes acuerdan una garantía de dos años sobre la instalación",
                ),
        )

        val passage = checkNotNull(hits("garantia").single().passage)

        assertEquals(2, passage.pageIndex)
        assertTrue(passage.text, "garantía de dos años" in passage.text)
        assertEquals(
            listOf("garantía"),
            passage.highlights.map { passage.text.substring(it.start, it.end) },
        )
    }

    @Test
    fun aLongPageIsShownAsAFragmentAroundTheMatch() = runBlocking {
        val filler = (1..80).joinToString(" ") { "palabra$it" }
        document("a", title = "Informe", pages = listOf("$filler la clave está aquí $filler"))

        val passage = checkNotNull(hits("clave").single().passage)

        assertTrue(passage.text, passage.text.length < 200)
        assertTrue(passage.text, passage.text.startsWith("…") && passage.text.endsWith("…"))
        assertEquals(
            listOf("clave"),
            passage.highlights.map { passage.text.substring(it.start, it.end) },
        )
    }

    // A document matched on several pages is shown once, by the page that matches best.
    @Test
    fun ofSeveralMatchingPagesTheBestOneIsShown() = runBlocking {
        val filler = (1..60).joinToString(" ") { "palabra$it" }
        document(
            "a",
            title = "Informe",
            pages = listOf("$filler factura $filler", "factura factura factura", "sin relación"),
        )

        val hit = hits("factura").single()

        assertEquals(1, hit.passage?.pageIndex)
    }

    @Test
    fun aMatchOnlyInWhatADocumentIsCalledHasNoPassage() = runBlocking {
        document("a", title = "Factura", pages = listOf("nada que ver"))

        assertNull(hits("factura").single().passage)
    }

    // Brackets and asterisks are the usual marks around a match, and a document has its own.
    @Test
    fun whatLooksLikeAMarkInThePagesTextIsShownAsText() = runBlocking {
        document("a", title = "Notas", pages = listOf("ver [anexo] y *nota* sobre la fianza"))

        val passage = checkNotNull(hits("fianza").single().passage)

        assertEquals("ver [anexo] y *nota* sobre la fianza", passage.text)
        assertEquals(
            listOf("fianza"),
            passage.highlights.map { passage.text.substring(it.start, it.end) },
        )
    }

    // --- building a library to search ---

    /** A document of the library, with the text of its pages as if it had already been read. */
    private suspend fun document(
        uuid: String,
        title: String? = null,
        description: String? = null,
        originalName: String = "Scan_$uuid",
        pages: List<String> = emptyList(),
    ) {
        repository.addScan(
            NewScan(
                uuid = uuid,
                originalName = originalName,
                filePath = "documents/$uuid.pdf",
                sizeBytes = 1,
                contentHash = "hash-$uuid",
                pageCount = pages.size.coerceAtLeast(1),
                capturedAtEpochMillis = 1,
            )
        )
        if (title != null || description != null) repository.modifyFields(uuid, title, description)
        pages.forEachIndexed { index, text ->
            db.execSQL(
                "INSERT INTO page_texts (page_id, text) " +
                    "SELECT p.id, ? FROM pages p JOIN documents d ON d.id = p.document_id " +
                    "WHERE d.uuid = ? AND p.page_index = ?",
                arrayOf<Any?>(text, uuid, index),
            )
        }
    }

    private fun usedAt(uuid: String, at: Long) {
        db.execSQL(
            "UPDATE document_activity SET last_activity_at = ? " +
                "WHERE document_id = (SELECT id FROM documents WHERE uuid = ?)",
            arrayOf<Any?>(at, uuid),
        )
    }

    private suspend fun hits(query: String): List<SearchHit> = index.search(query)

    private suspend fun search(query: String): List<String> = hits(query).map { it.documentUuid }
}
