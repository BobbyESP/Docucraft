/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.documentcontent.platform.links

import com.bobbyesp.documentcontent.NormalizedPoint
import com.bobbyesp.documentcontent.NormalizedRect
import com.bobbyesp.documentcontent.PageLink
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InternalLinkReaderTest {

    // ------------------------------------------------------------------------ the shared fixtures

    @Test
    fun `the fixture's three internal links, where the manifest puts them`() {
        val links = reader(fixture("text-and-links.pdf")).links(0)

        assertEquals(3, links.size)
        // "goto-xyz": /XYZ 72 500 on an A4 page.
        assertLink(links[0], NormalizedRect(0.4114f, 0.2627f, 0.5244f, 0.2758f), 2, A4_72_500)
        // "goto-fit": the page, nowhere in particular.
        assertLink(links[1], NormalizedRect(0.4114f, 0.296f, 0.5244f, 0.309f), 2, null)
        assertLink(links[2], NormalizedRect(0.3832f, 0.3292f, 0.5385f, 0.3423f), 2, A4_72_500)
    }

    @Test
    fun `web links are left to the platform`() {
        // Page 0 also has five web links, a mailto and a tel: none of them is read here.
        val reader = reader(fixture("text-and-links.pdf"))
        assertTrue(
            (0 until reader.pageCount).flatMap { reader.links(it) }.all { it.pageIndex == 2 }
        )
    }

    @Test
    fun `a table of contents written by another tool leads to pages the document has`() {
        // prueba_motor_pdf.pdf comes from ReportLab: its contents and "back to index" links are
        // explicit /XYZ destinations.
        val reader = reader(fixture("prueba_motor_pdf.pdf"))
        val links = (0 until reader.pageCount).flatMap { reader.links(it) }

        assertTrue("found ${links.size}", links.size >= 10)
        for (link in links) {
            assertTrue(link.pageIndex in 0 until reader.pageCount)
            val position = link.position!!
            assertTrue(position.x in 0f..1f && position.y in 0f..1f)
            assertTrue(link.bounds.single().width > 0f)
        }
    }

    // ---------------------------------------------------------------------------- destinations

    @Test
    fun `an explicit destination, placed on the page as displayed`() {
        val reader = reader(threePages(annotation = "/Dest [{p2} /XYZ 72 500 null]").classic(ROOT))

        val link = reader.links(0).single()
        assertLink(link, A4_LINK, 2, A4_72_500)
    }

    @Test
    fun `the same document, compressed into object streams, reads the same`() {
        val pdf = threePages(annotation = "/Dest [{p2} /XYZ 72 500 null]")
        val compressed = pdf.compressed(ROOT, compressed = (1..pdf.size).toSet())

        assertLink(reader(compressed).links(0).single(), A4_LINK, 2, A4_72_500)
    }

    @Test
    fun `named destinations, from the names tree and from the catalog`() {
        val pdf = newPdf()
        val pages = pdf.pageRefs(3)
        val tree = pdf.reserve()
        val first = pdf.add("<< /Limits [(a) (m)] /Names [(chapter.1) [${pages[1]} 0 R /Fit]] >>")
        val second =
            pdf.add(
                "<< /Limits [(n) (z)] /Names [(section.2) << /D [${pages[2]} 0 R /FitH 400] >>] >>"
            )
        pdf.put(tree, "<< /Kids [$first 0 R $second 0 R] >>")
        pdf.writePages(
            pages,
            "/Annots [" +
                link("/A << /S /GoTo /D (section.2) >>") +
                link("/Dest (chapter.1)") +
                link("/Dest /OldStyle") +
                link("/Dest (missing)") +
                "]",
        )
        pdf.put(
            ROOT,
            "<< /Type /Catalog /Pages ${PAGES} 0 R /Names << /Dests $tree 0 R >> " +
                "/Dests << /OldStyle [${pages[0]} 0 R /FitR 10 20 30 40] >> >>",
        )

        val links = reader(pdf.classic(ROOT)).links(0)

        assertEquals(listOf(2, 1, 0), links.map { it.pageIndex })
        assertPoint(NormalizedPoint(0f, (842f - 400f) / 842f), links[0].position)
        assertNull(links[1].position)
        assertPoint(NormalizedPoint(10f / 595f, (842f - 40f) / 842f), links[2].position)
    }

    @Test
    fun `next and previous page actions, within the document`() {
        val pdf = newPdf()
        val pages = pdf.pageRefs(2)
        pdf.writePages(
            pages,
            "/Annots [" +
                link("/A << /S /Named /N /NextPage >>") +
                link("/A << /S /Named /N /PrevPage >>") +
                link("/A << /S /Named /N /LastPage >>") +
                link("/A << /S /URI /URI (https://example.com) >>") +
                "]",
            everyPage = true,
        )
        pdf.catalog()

        val reader = reader(pdf.classic(ROOT))
        // No page before the first: that one is left out, and so is the web link.
        assertEquals(listOf(1, 1), reader.links(0).map { it.pageIndex })
        assertEquals(listOf(0, 1), reader.links(1).map { it.pageIndex })
    }

    // -------------------------------------------------------------------------------- geometry

    @Test
    fun `a rotated, cropped page places the link and its target as it is displayed`() {
        val pdf = newPdf()
        val page = pdf.reserve()
        pdf.put(PAGES, "<< /Type /Pages /Kids [$page 0 R] /Count 1 /Rotate 90 >>")
        pdf.put(
            page,
            "<< /Type /Page /Parent $PAGES 0 R /MediaBox [0 0 600 800] /CropBox [100 100 500 700] " +
                "/Annots [<< /Subtype /Link /Rect [100 600 300 700] /Dest [$page 0 R /XYZ 100 700 0] >>] >>",
        )
        pdf.catalog()

        val link = reader(pdf.classic(ROOT)).links(0).single()

        // The crop box's top left quarter, turned a quarter clockwise, is along its top right.
        assertRect(NormalizedRect(1f - 100f / 600f, 0f, 1f, 0.5f), link.bounds.single())
        assertPoint(NormalizedPoint(1f, 0f), link.position)
    }

    @Test
    fun `a link over two lines is one box per line`() {
        val pdf = newPdf()
        val pages = pdf.pageRefs(1)
        pdf.writePages(
            pages,
            "/Annots [<< /Subtype /Link /Rect [50 600 300 650] /Dest [${pages[0]} 0 R /Fit] " +
                "/QuadPoints [50 650 300 650 50 630 300 630 50 620 120 620 50 600 120 600] >>]",
        )
        pdf.catalog()

        val bounds = reader(pdf.classic(ROOT)).links(0).single().bounds

        assertEquals(2, bounds.size)
        assertRect(NormalizedRect(50f / 595f, 192f / 842f, 300f / 595f, 212f / 842f), bounds[0])
        assertRect(NormalizedRect(50f / 595f, 222f / 842f, 120f / 595f, 242f / 842f), bounds[1])
    }

    // ------------------------------------------------------------------------- damaged files

    @Test
    fun `offsets that point to the wrong places are recovered`() {
        val bytes =
            threePages(annotation = "/Dest [{p2} /XYZ 72 500 null]").classic(ROOT, shiftOffsets = 7)
        assertLink(reader(bytes).links(0).single(), A4_LINK, 2, A4_72_500)
    }

    @Test
    fun `a file without a cross-reference at all is recovered`() {
        val bytes = threePages(annotation = "/Dest [{p2} /XYZ 72 500 null]").classic(ROOT)
        val text = String(bytes, Charsets.ISO_8859_1)
        val stripped = text.substringBefore("xref\n0 ").toByteArray(Charsets.ISO_8859_1)

        assertLink(reader(stripped).links(0).single(), A4_LINK, 2, A4_72_500)
    }

    @Test
    fun `the newest revision of an incrementally saved file wins`() {
        val pdf = threePages(annotation = "/Dest [{p2} /Fit]")
        val base = pdf.classic(ROOT)
        val firstPage = FIRST_PAGE
        val updated =
            TestPdf.update(
                base,
                root = ROOT,
                size = pdf.size + 1,
                changed =
                    mapOf(
                        firstPage to
                            "<< /Type /Page /Parent $PAGES 0 R /MediaBox [0 0 595 842] " +
                                "/Annots [${link("/Dest [${firstPage + 1} 0 R /Fit]")}] >>"
                    ),
            )

        assertEquals(1, reader(updated).links(0).single().pageIndex)
    }

    @Test
    fun `an encrypted document still gives its explicit destinations`() {
        val pdf = threePages(annotation = "/Dest [{p2} /XYZ 72 500 null]")
        val encrypt = pdf.add("<< /Filter /Standard /V 1 /R 2 /O (x) /U (y) /P -4 >>")
        val bytes = pdf.classic(ROOT, trailerExtra = "/Encrypt $encrypt 0 R")

        assertLink(reader(bytes).links(0).single(), A4_LINK, 2, A4_72_500)
    }

    @Test
    fun `a page tree that contains itself ends`() {
        val pdf = newPdf()
        val page = pdf.reserve()
        pdf.put(PAGES, "<< /Type /Pages /Kids [$PAGES 0 R $page 0 R] /Count 1 >>")
        pdf.put(page, "<< /Type /Page /Parent $PAGES 0 R /MediaBox [0 0 595 842] >>")
        pdf.catalog()

        assertEquals(1, reader(pdf.classic(ROOT)).pageCount)
    }

    @Test
    fun `what is not a PDF fails to open, so the caller has no links to offer`() {
        val bytes = "Not a PDF at all".toByteArray()
        assertTrue(runCatching { InternalLinkReader(ByteArrayBytes(bytes)) }.isFailure)
    }

    // ---------------------------------------------------------------------------------- helpers

    private fun reader(bytes: ByteArray) = InternalLinkReader(ByteArrayBytes(bytes))

    private fun link(target: String) = "<< /Subtype /Link /Rect [100 700 200 720] $target >>"

    /**
     * Three A4 pages; the first has one link, [annotation], in which `{p2}` stands for the third
     * page.
     */
    private fun threePages(annotation: String): TestPdf {
        val pdf = newPdf()
        val pages = pdf.pageRefs(3)
        val resolved = annotation.replace("{p2}", "${pages[2]} 0 R")
        pdf.writePages(pages, "/Annots [<< /Subtype /Link /Rect [100 700 200 720] $resolved >>]")
        pdf.catalog()
        return pdf
    }

    private fun assertLink(
        link: PageLink.Internal,
        bounds: NormalizedRect,
        page: Int,
        position: NormalizedPoint?,
    ) {
        assertEquals(page, link.pageIndex)
        assertRect(bounds, link.bounds.single())
        if (position == null) assertNull(link.position) else assertPoint(position, link.position)
    }

    private fun assertRect(expected: NormalizedRect, actual: NormalizedRect) {
        assertEquals("left", expected.left, actual.left, TOLERANCE)
        assertEquals("top", expected.top, actual.top, TOLERANCE)
        assertEquals("right", expected.right, actual.right, TOLERANCE)
        assertEquals("bottom", expected.bottom, actual.bottom, TOLERANCE)
    }

    private fun assertPoint(expected: NormalizedPoint, actual: NormalizedPoint?) {
        assertEquals("x", expected.x, actual!!.x, TOLERANCE)
        assertEquals("y", expected.y, actual.y, TOLERANCE)
    }

    private companion object {
        /** The manifest rounds to four decimals. */
        const val TOLERANCE = 2e-4f

        const val ROOT = 1
        const val PAGES = 2

        /** /XYZ 72 500 on an A4 page. */
        val A4_72_500 = NormalizedPoint(72f / 595f, (842f - 500f) / 842f)

        /** [100 700 200 720] on an A4 page. */
        val A4_LINK = NormalizedRect(100f / 595f, 122f / 842f, 200f / 595f, 142f / 842f)

        /** The fixtures every module's device tests read; this test reads them on the JVM. */
        fun fixture(name: String): ByteArray {
            var dir: File? = File(System.getProperty("user.dir")!!).absoluteFile
            while (dir != null) {
                val file = File(dir, "composepdf/src/androidTest/assets/fixtures/$name")
                if (file.isFile) return file.readBytes()
                dir = dir.parentFile
            }
            error("Fixture $name not found")
        }
    }
}

/** A document whose catalog is object 1 and page tree object 2, both still to be written. */
private fun newPdf() =
    TestPdf().apply {
        reserve()
        reserve()
    }

/** The first page reserved after them. */
private const val FIRST_PAGE = 3

private fun TestPdf.pageRefs(count: Int): List<Int> = List(count) { reserve() }

/** @param extra More of the first page's dictionary, or of every page's with [everyPage]. */
private fun TestPdf.writePages(pages: List<Int>, extra: String, everyPage: Boolean = false) {
    put(
        2,
        "<< /Type /Pages /Kids [${pages.joinToString(" ") { "$it 0 R" }}] /Count ${pages.size} >>",
    )
    pages.forEachIndexed { index, page ->
        val extra = if (index == 0 || everyPage) extra else ""
        put(page, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] $extra >>")
    }
}

private fun TestPdf.catalog() {
    put(1, "<< /Type /Catalog /Pages 2 0 R >>")
}
