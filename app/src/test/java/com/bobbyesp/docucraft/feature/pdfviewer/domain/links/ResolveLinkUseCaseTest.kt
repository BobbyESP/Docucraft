/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.domain.links

import com.bobbyesp.documentcontent.NormalizedPoint
import com.bobbyesp.documentcontent.NormalizedRect
import com.bobbyesp.documentcontent.PageLink
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolveLinkUseCaseTest {

    private val resolve = ResolveLinkUseCase()

    // ------------------------------------------------------------------ the web

    @Test
    fun `an https link opens, by its host`() {
        assertEquals(
            LinkAction.OpenWeb("https://example.com/docucraft?q=1", "example.com", secure = true),
            external("https://example.com/docucraft?q=1"),
        )
    }

    @Test
    fun `a plain http link opens too, marked as not secure`() {
        assertEquals(
            LinkAction.OpenWeb("http://example.com/insecure", "example.com", secure = false),
            external("http://example.com/insecure"),
        )
    }

    @Test
    fun `the host shown is the real one, not what sits before an at sign`() {
        val action = external("https://mybank.com@elsewhere.com/login") as LinkAction.OpenWeb
        assertEquals("elsewhere.com", action.host)
    }

    /** The host shown must be the one a browser goes to, which reads URLs by the WHATWG rules. */
    @Test
    fun `the host is read as a browser reads it`() {
        // A backslash ends the host, as a slash would.
        assertEquals(
            "mybank.com",
            (external("https://mybank.com\\@elsewhere.com/") as LinkAction.OpenWeb).host,
        )
        // Tabs and line breaks inside are dropped.
        assertEquals(
            "elsewhere.com",
            (external("https://mybank.com@else\twhere.com/") as LinkAction.OpenWeb).host,
        )
    }

    @Test
    fun `the host leaves out port, case and a trailing dot`() {
        val action = external("HTTPS://Example.COM.:8443/path") as LinkAction.OpenWeb
        assertEquals("example.com", action.host)
    }

    @Test
    fun `an internationalized host is shown in punycode, which gives a look-alike away`() {
        // "аpple.com" with a Cyrillic "а".
        val action = external("https://аpple.com/") as LinkAction.OpenWeb
        assertEquals("xn--pple-43d.com", action.host)
        // An honest one reads just as oddly, and that is the point: nothing passes as ASCII.
        assertEquals("xn--bcher-kva.de", (external("https://bücher.de") as LinkAction.OpenWeb).host)
    }

    @Test
    fun `an IPv6 host is kept whole`() {
        val action = external("http://[2001:db8::1]:8080/") as LinkAction.OpenWeb
        assertEquals("[2001:db8::1]", action.host)
    }

    @Test
    fun `a bare www address is taken as the secure web`() {
        assertEquals(
            LinkAction.OpenWeb("https://www.example.com/a", "www.example.com", secure = true),
            external("www.example.com/a"),
        )
    }

    @Test
    fun `a web link without a host goes nowhere`() {
        assertEquals(
            BlockReason.Malformed,
            (external("https:///path") as LinkAction.Blocked).reason,
        )
        assertEquals(BlockReason.Malformed, (external("https://") as LinkAction.Blocked).reason)
    }

    // ------------------------------------------------------------------ email and phone

    @Test
    fun `mailto composes an email to its address`() {
        assertEquals(
            LinkAction.ComposeEmail("mailto:hola@example.com?subject=Hi", "hola@example.com"),
            external("mailto:hola@example.com?subject=Hi"),
        )
    }

    @Test
    fun `an encoded address is decoded`() {
        val action = external("mailto:ana%2Bpdf@example.com") as LinkAction.ComposeEmail
        assertEquals("ana+pdf@example.com", action.address)
    }

    @Test
    fun `tel dials its number, without its parameters`() {
        assertEquals(
            LinkAction.Dial("tel:+34600000000;ext=12", "+34600000000"),
            external("tel:+34600000000;ext=12"),
        )
    }

    @Test
    fun `email and phone links with nothing to reach are refused`() {
        assertEquals(BlockReason.Malformed, (external("mailto:") as LinkAction.Blocked).reason)
        assertEquals(BlockReason.Malformed, (external("tel:call-me") as LinkAction.Blocked).reason)
    }

    // ------------------------------------------------------------------ blocked

    @Test
    fun `only the four safe schemes are ever followed`() {
        for (target in
            listOf(
                "javascript:alert(1)",
                "JavaScript:alert(1)",
                "file:///sdcard/secret.pdf",
                "intent://scan/#Intent;scheme=zxing;end",
                "content://com.example.provider/data",
                "data:text/html,<script>alert(1)</script>",
                "ftp://example.com/file",
                "market://details?id=com.example",
            )) {
            assertEquals(
                target,
                LinkAction.Blocked(target, BlockReason.Scheme),
                external(target),
            )
        }
    }

    @Test
    fun `what is not a link at all is refused`() {
        assertEquals(BlockReason.Malformed, (external("not a link") as LinkAction.Blocked).reason)
        assertEquals(BlockReason.Malformed, (external("") as LinkAction.Blocked).reason)
        assertEquals(BlockReason.Malformed, (external("1http://x") as LinkAction.Blocked).reason)
    }

    @Test
    fun `surrounding spaces are not part of a link`() {
        assertEquals(
            LinkAction.OpenWeb("https://example.com", "example.com", secure = true),
            external("  https://example.com \n"),
        )
    }

    // ------------------------------------------------------------------ inside the document

    @Test
    fun `an internal link goes to its page`() {
        val position = NormalizedPoint(0.1f, 0.4f)
        assertEquals(
            LinkAction.GoTo(2, position),
            resolve(PageLink.Internal(Area, pageIndex = 2, position = position), pageCount = 3),
        )
    }

    @Test
    fun `an internal link past the document goes nowhere`() {
        assertEquals(
            LinkAction.Blocked("page 4", BlockReason.OutsideDocument),
            resolve(PageLink.Internal(Area, pageIndex = 3, position = null), pageCount = 3),
        )
        assertEquals(
            BlockReason.OutsideDocument,
            (resolve(PageLink.Internal(Area, pageIndex = -1, position = null), pageCount = 3)
                    as LinkAction.Blocked)
                .reason,
        )
    }

    private fun external(uri: String) = resolve(PageLink.External(Area, uri), pageCount = 3)

    private companion object {
        val Area = listOf(NormalizedRect(0.1f, 0.1f, 0.3f, 0.12f))
    }
}
