/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.feature.docscanner.FakeDocumentIndexQueue
import com.bobbyesp.docucraft.feature.docscanner.FakeDocumentStorage
import com.bobbyesp.docucraft.feature.docscanner.FakeDocumentsRepository
import com.bobbyesp.docucraft.feature.docscanner.FakePagesRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.model.PageTextStatus
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.PageTextRecord
import com.bobbyesp.docucraft.feature.docscanner.testDocument
import com.bobbyesp.docucraft.feature.docscanner.testLinkedDocument
import com.bobbyesp.documentcontent.ContentOrigin
import com.bobbyesp.documentcontent.DocumentSource
import com.bobbyesp.documentcontent.NormalizedRect
import com.bobbyesp.documentcontent.PageContentProvider
import com.bobbyesp.documentcontent.PageContentResult
import com.bobbyesp.documentcontent.PageContentSession
import com.bobbyesp.documentcontent.PageText
import com.bobbyesp.documentcontent.TextLine
import com.bobbyesp.documentcontent.TextWord
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading the text of a document's pages so that it can be searched. The document's content is a
 * script of what each read of each page gives, so that every way a page can turn out is a line.
 */
class TextIndexingUseCasesTest {

    private val pages = FakePagesRepository()
    private val storage = FakeDocumentStorage()
    private val content = ScriptedContent()
    private val queue = FakeDocumentIndexQueue()
    private val recognition = ScriptedContent(ContentOrigin.RECOGNIZED)

    // --- reading a document ---

    @Test
    fun `the text of each page is written down, in reading order`() = runTest {
        pages.pending("doc-1", 2)
        content.page(0, text(listOf("Invoice", "42"), listOf("Total", "due")))
        content.page(1, text(listOf("Thanks")))

        index(testDocument(uuid = "doc-1", pageCount = 2))

        assertEquals(
            PageTextRecord("Invoice 42\nTotal due", ContentOrigin.EMBEDDED, null, "platform"),
            pages.texts["doc-1" to 0],
        )
        assertEquals("Thanks", pages.texts["doc-1" to 1]?.text)
        assertEquals(
            listOf(PageTextStatus.EXTRACTED, PageTextStatus.EXTRACTED),
            pages.statusOf("doc-1"),
        )
        assertEquals(IndexDocumentTextUseCase.EXTRACTOR_VERSION, pages.versions["doc-1" to 0])
    }

    /** It is read from where the catalogue says the document is, and let go of afterwards. */
    @Test
    fun `the document is opened once, where it is, and closed`() = runTest {
        pages.pending("doc-1", 3)
        val document = testDocument(uuid = "doc-1", pageCount = 3)

        index(document)

        assertEquals(listOf(DocumentSource(document.location.value)), content.opened)
        assertEquals(1, content.closed)
    }

    /** Text recognition is the user's to turn on, and until then such a page waits for it. */
    @Test
    fun `a page with no text of its own waits for text recognition`() = runTest {
        pages.pending("doc-1", 4)
        content.page(0, PageContentResult.NoText)
        content.page(1, PageContentResult.Unsupported)
        content.page(2, PageContentResult.Available(text = null, links = emptyList()))
        content.page(3, text(emptyList()))

        index(testDocument(uuid = "doc-1", pageCount = 4))

        assertEquals(List(4) { PageTextStatus.OCR_DISABLED }, pages.statusOf("doc-1"))
        assertTrue(pages.texts.isEmpty())
    }

    @Test
    fun `only the pages still to be read are read`() = runTest {
        pages.pending("doc-1", 3)
        pages.storeText("doc-1", 1, PageTextRecord("kept", ContentOrigin.EMBEDDED, null, null), 1)

        index(testDocument(uuid = "doc-1", pageCount = 3))

        assertEquals(listOf(0, 2), content.read)
        assertEquals("kept", pages.texts["doc-1" to 1]?.text)
    }

    @Test
    fun `a document with nothing to read is not opened`() = runTest {
        pages.pending("doc-1", 1)
        pages.storeWithoutText("doc-1", 0, PageTextStatus.OCR_DISABLED, 1)

        index(testDocument(uuid = "doc-1", pageCount = 1))

        assertTrue(content.opened.isEmpty())
    }

    // --- pages that cannot be read ---

    @Test
    fun `a page that fails is tried again, and read if it then works`() = runTest {
        pages.pending("doc-1", 1)
        content.page(0, failed(), failed(), text(listOf("third", "time")))

        index(testDocument(uuid = "doc-1", pageCount = 1))

        assertEquals("third time", pages.texts["doc-1" to 0]?.text)
        assertEquals(listOf(PageTextStatus.EXTRACTED), pages.statusOf("doc-1"))
        assertEquals(0, pages.pagesOf("doc-1").single().attempts)
    }

    @Test
    fun `a page that fails three times is given up on, and the rest are still read`() = runTest {
        pages.pending("doc-1", 2)
        content.page(0, failed(), failed(), failed(), text(listOf("never", "reached")))
        content.page(1, text(listOf("fine")))

        index(testDocument(uuid = "doc-1", pageCount = 2))

        assertEquals(
            listOf(PageTextStatus.FAILED, PageTextStatus.EXTRACTED),
            pages.statusOf("doc-1"),
        )
        assertEquals(listOf(0, 0, 0, 1), content.read)
    }

    /** A provider is asked not to throw, and one that does must not take the document with it. */
    @Test
    fun `a read that throws counts as one that failed`() = runTest {
        pages.pending("doc-1", 1)
        content.page(0, IllegalStateException("renderer closed"), text(listOf("ok")))

        index(testDocument(uuid = "doc-1", pageCount = 1))

        assertEquals("ok", pages.texts["doc-1" to 0]?.text)
    }

    /** Being stopped is not the page's fault: it stays pending, with no failure against it. */
    @Test
    fun `being cancelled is not a failure of the page`() = runTest {
        pages.pending("doc-1", 1)
        content.page(0, CancellationException("stopped"))

        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking { index(testDocument(uuid = "doc-1", pageCount = 1)) }
        }

        assertEquals(0, pages.pagesOf("doc-1").single().attempts)
        assertEquals(1, content.closed)
    }

    // --- text recognition ---

    /** The user turns it on for a document. Until then its images are not read. */
    @Test
    fun `a page with no text of its own is not recognized unless the document asks for it`() =
        runTest {
            pages.pending("doc-1", 1)
            content.page(0, PageContentResult.NoText)
            recognition.page(0, recognized(listOf("never", "asked")))

            index(testDocument(uuid = "doc-1", pageCount = 1))

            assertTrue(recognition.opened.isEmpty())
            assertEquals(listOf(PageTextStatus.OCR_DISABLED), pages.statusOf("doc-1"))
        }

    @Test
    fun `with recognition on, a page with no text of its own is read from its image`() = runTest {
        pages.pending("doc-1", 1)
        content.page(0, PageContentResult.NoText)
        recognition.page(0, recognized(listOf("Factura", "2026")))

        index(recognizing("doc-1", pageCount = 1))

        val record = checkNotNull(pages.texts["doc-1" to 0])
        assertEquals("Factura 2026", record.text)
        assertEquals(ContentOrigin.RECOGNIZED, record.origin)
        assertEquals(0.9f, record.confidence)
        assertEquals("test-engine", record.engine)
        assertEquals("es", record.language)
        // Where its words are is kept with it: getting it back costs a recognition.
        assertEquals(listOf("Factura", "2026"), record.layout?.single()?.words?.map { it.text })
        assertEquals(listOf(PageTextStatus.EXTRACTED), pages.statusOf("doc-1"))
    }

    /** Recognition is slow. A page the PDF already gives the text of is never sent to it. */
    @Test
    fun `the document's own text comes first, and only the other pages are recognized`() = runTest {
        pages.pending("doc-1", 2)
        content.page(0, text(listOf("typed")))
        content.page(1, PageContentResult.Unsupported)
        recognition.page(1, recognized(listOf("scanned")))

        index(recognizing("doc-1", pageCount = 2))

        assertEquals(listOf(1), recognition.read)
        assertEquals(ContentOrigin.EMBEDDED, pages.texts["doc-1" to 0]?.origin)
        assertNull(pages.texts["doc-1" to 0]?.layout)
        assertEquals(ContentOrigin.RECOGNIZED, pages.texts["doc-1" to 1]?.origin)
        assertEquals(1, recognition.closed)
    }

    @Test
    fun `an image recognition finds no words in has no text, and that is not asked again`() =
        runTest {
            pages.pending("doc-1", 1)
            content.page(0, PageContentResult.NoText)
            recognition.page(0, PageContentResult.NoText)

            index(recognizing("doc-1", pageCount = 1))

            assertEquals(listOf(PageTextStatus.NO_TEXT), pages.statusOf("doc-1"))
            assertTrue(pages.pagesToRead("doc-1").isEmpty())
        }

    /** Such as the model not having arrived yet: it is tried again the next time the app starts. */
    @Test
    fun `a recognition that fails is a failure of the page`() = runTest {
        pages.pending("doc-1", 1)
        content.page(0, PageContentResult.NoText)
        recognition.page(0, failed())

        index(recognizing("doc-1", pageCount = 1))

        assertEquals(listOf(PageTextStatus.FAILED), pages.statusOf("doc-1"))
        assertEquals(listOf(0, 0, 0), recognition.read)
    }

    @Test
    fun `without a recognition engine every such page waits`() = runTest {
        pages.pending("doc-1", 1)
        content.page(0, PageContentResult.NoText)
        val document = recognizing("doc-1", pageCount = 1)

        IndexDocumentTextUseCase(
            FakeDocumentsRepository(documents = listOf(document)),
            pages,
            storage,
            content,
            recognized = null,
        )("doc-1")

        assertEquals(listOf(PageTextStatus.OCR_DISABLED), pages.statusOf("doc-1"))
    }

    /** It is turned on from the document's actions, perhaps while the document is being read. */
    @Test
    fun `recognition turned on while the document is read reaches every page in the same go`() =
        runTest {
            pages.pending("doc-1", 2)
            content.page(0, PageContentResult.NoText)
            content.page(1, PageContentResult.NoText)
            recognition.page(0, recognized(listOf("first")))
            recognition.page(1, recognized(listOf("second")))
            val documents =
                FakeDocumentsRepository(
                    documents = listOf(testDocument(uuid = "doc-1", pageCount = 2))
                )
            // As the second page is read: the first was already left waiting for recognition.
            var turnedOn = false
            content.onRead = { index ->
                if (index == 1 && !turnedOn) {
                    turnedOn = true
                    kotlinx.coroutines.runBlocking { pages.setTextRecognition("doc-1", true) }
                    documents.documents.value = listOf(recognizing("doc-1", pageCount = 2))
                }
            }

            IndexDocumentTextUseCase(documents, pages, storage, content, recognition)("doc-1")

            assertEquals("first", pages.texts["doc-1" to 0]?.text)
            assertEquals("second", pages.texts["doc-1" to 1]?.text)
        }

    @Test
    fun `turning recognition on makes the waiting pages pending and queues the document`() =
        runTest {
            pages.pending("doc-1", 2)
            pages.storeWithoutText("doc-1", 0, PageTextStatus.OCR_DISABLED, 1)
            pages.storeText(
                "doc-1",
                1,
                PageTextRecord("typed", ContentOrigin.EMBEDDED, null, null),
                1,
            )

            assertTrue(SetDocumentTextRecognitionUseCase(pages, queue)("doc-1", enabled = true))

            assertEquals(listOf(0), pages.pagesToRead("doc-1"))
            assertEquals(listOf("doc-1"), queue.queued)
        }

    @Test
    fun `turning recognition off forgets what was recognized, keeps the rest and queues nothing`() =
        runTest {
            pages.pending("doc-1", 2)
            pages.storeText(
                "doc-1",
                0,
                PageTextRecord("typed", ContentOrigin.EMBEDDED, null, null),
                1,
            )
            pages.storeText(
                "doc-1",
                1,
                PageTextRecord("scanned", ContentOrigin.RECOGNIZED, 0.8f, "test-engine"),
                1,
            )

            assertTrue(SetDocumentTextRecognitionUseCase(pages, queue)("doc-1", enabled = false))

            assertEquals("typed", pages.texts["doc-1" to 0]?.text)
            assertNull(pages.texts["doc-1" to 1])
            assertEquals(
                listOf(PageTextStatus.EXTRACTED, PageTextStatus.OCR_DISABLED),
                pages.statusOf("doc-1"),
            )
            assertTrue(queue.queued.isEmpty())
        }

    @Test
    fun `recognition cannot be set for a document that is not there, and nothing is queued`() =
        runTest {
            assertFalse(SetDocumentTextRecognitionUseCase(pages, queue)("gone", enabled = true))
            assertTrue(queue.queued.isEmpty())
        }

    // --- documents that are not read ---

    @Test
    fun `a document deleted while it is read ends the reading, without error`() = runTest {
        pages.pending("doc-1", 3)
        content.onRead = { index -> if (index == 1) pages.pages.remove("doc-1") }

        index(testDocument(uuid = "doc-1", pageCount = 3))

        assertEquals(listOf(0, 1), content.read)
        assertEquals(1, content.closed)
    }

    @Test
    fun `a document that is gone, in the bin or another app's is left alone`() = runTest {
        pages.pending("binned", 1)
        val documents =
            FakeDocumentsRepository(
                documents = listOf(testDocument(uuid = "binned").copy(trashedAtEpochMillis = 5L)),
                linked = listOf(testLinkedDocument(uuid = "linked-1")),
            )
        val index = IndexDocumentTextUseCase(documents, pages, storage, content, recognition)

        index("binned")
        index("linked-1")
        index("no-such-document")

        assertTrue(content.opened.isEmpty())
        assertEquals(listOf(PageTextStatus.PENDING), pages.statusOf("binned"))
    }

    // --- documents whose pages were never counted ---

    /** A document from an older catalogue can come without its number of pages. */
    @Test
    fun `a document whose pages were never counted gets them counted, then read`() = runTest {
        pages.uncounted += "doc-1"
        storage.files += "documents/doc-1.pdf"
        storage.pageCount = 2
        content.page(0, text(listOf("first")))

        index(testDocument(uuid = "doc-1", pageCount = null))

        assertEquals(
            listOf(PageTextStatus.EXTRACTED, PageTextStatus.OCR_DISABLED),
            pages.statusOf("doc-1"),
        )
    }

    @Test
    fun `one whose file cannot be counted is left for another time`() = runTest {
        pages.uncounted += "doc-1"

        index(testDocument(uuid = "doc-1", pageCount = null))

        assertTrue("doc-1" in pages.uncounted)
        assertTrue(content.opened.isEmpty())
    }

    // --- picking up where it was left ---

    @Test
    fun `starting queues every document with something left to read`() = runTest {
        pages.pending("unread", 2)
        pages.pending("read", 1)
        pages.storeWithoutText("read", 0, PageTextStatus.OCR_DISABLED, 1)
        pages.uncounted += "uncounted"

        ResumeTextIndexingUseCase(pages, queue)()

        assertEquals(setOf("unread", "uncounted"), queue.queued.toSet())
        assertEquals(listOf(IndexDocumentTextUseCase.EXTRACTOR_VERSION), pages.requeued)
    }

    /** What failed a page, such as a file out of reach, may be over. */
    @Test
    fun `starting tries again the pages that failed`() = runTest {
        pages.pending("doc-1", 1)
        repeat(3) { pages.recordFailure("doc-1", 0, maxAttempts = 3) }
        assertEquals(listOf(PageTextStatus.FAILED), pages.statusOf("doc-1"))

        ResumeTextIndexingUseCase(pages, queue)()

        assertEquals(listOf("doc-1"), queue.queued)
        assertEquals(listOf(PageTextStatus.PENDING), pages.statusOf("doc-1"))
    }

    @Test
    fun `starting with nothing left to read queues nothing`() = runTest {
        ResumeTextIndexingUseCase(pages, queue)()

        assertTrue(queue.queued.isEmpty())
        assertFalse(pages.requeued.isEmpty())
        assertNull(pages.texts["doc-1" to 0])
    }

    private suspend fun index(document: Document.Managed) {
        IndexDocumentTextUseCase(
            FakeDocumentsRepository(documents = listOf(document)),
            pages,
            storage,
            content,
            recognition,
        )(document.uuid)
    }

    private fun recognizing(uuid: String, pageCount: Int) =
        testDocument(uuid = uuid, pageCount = pageCount).copy(ocrEnabled = true)

    private fun recognized(vararg lines: List<String>) =
        PageContentResult.Available(
            text =
                PageText(
                    lines = lines.map { words -> TextLine(words.map { TextWord(it, BOX) }) },
                    origin = ContentOrigin.RECOGNIZED,
                    confidence = 0.9f,
                    engine = "test-engine",
                    language = "es",
                ),
            links = emptyList(),
        )

    private fun failed() = PageContentResult.Failed(IllegalStateException("could not read"))

    private fun text(vararg lines: List<String>) =
        PageContentResult.Available(
            text =
                PageText(
                    lines = lines.map { words -> TextLine(words.map { TextWord(it, BOX) }) },
                    origin = ContentOrigin.EMBEDDED,
                ),
            links = emptyList(),
        )

    private companion object {
        val BOX = NormalizedRect(0f, 0f, 1f, 1f)
    }
}

/**
 * A document whose pages answer what they are told to, one answer per read; the last one is
 * repeated. An answer that is an exception is thrown. A page nothing was said about has no text.
 */
private class ScriptedContent(override val origin: ContentOrigin = ContentOrigin.EMBEDDED) :
    PageContentProvider {

    private val script = mutableMapOf<Int, ArrayDeque<Any>>()

    val opened = mutableListOf<DocumentSource>()
    var closed = 0

    /** The pages read, in order, once for each read. */
    val read = mutableListOf<Int>()

    /** Runs after each read, before its answer is given. */
    var onRead: (Int) -> Unit = {}

    fun page(index: Int, vararg answers: Any) {
        script[index] = ArrayDeque(answers.toList())
    }

    override suspend fun open(document: DocumentSource): PageContentSession {
        opened += document
        return object : PageContentSession {
            override suspend fun page(index: Int): PageContentResult {
                read += index
                onRead(index)
                val answers = script[index] ?: return PageContentResult.NoText
                val answer = if (answers.size > 1) answers.removeFirst() else answers.first()
                if (answer is Throwable) throw answer
                return answer as PageContentResult
            }

            override fun close() {
                closed++
            }
        }
    }
}
