/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.domain.links

import android.os.Build
import com.bobbyesp.documentcontent.NormalizedPoint
import com.bobbyesp.documentcontent.PageLink
import java.net.IDN
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** What following a link would do, decided before anything is opened. */
sealed interface LinkAction {

    /**
     * A web page.
     *
     * @param host Where it really goes: without any `user@` in front, without the port, and in
     *   ASCII (punycode for an internationalized name), so a look-alike shows as what it is. This
     *   is what the reader is shown before it opens (D3).
     * @param secure `false` for plain `http`.
     */
    data class OpenWeb(val url: String, val host: String, val secure: Boolean) : LinkAction

    /** A new email to [address]. */
    data class ComposeEmail(val uri: String, val address: String) : LinkAction

    /** The dialler, with [number] typed in. Nothing is called without the reader. */
    data class Dial(val uri: String, val number: String) : LinkAction

    /** A place in this document. */
    data class GoTo(val pageIndex: Int, val position: NormalizedPoint?) : LinkAction

    /** Not followed, for [reason]. */
    data class Blocked(val target: String, val reason: BlockReason) : LinkAction
}

enum class BlockReason {
    /** A scheme the viewer does not open: `javascript:`, `file:`, `intent:` and the like. */
    Scheme,

    /** Not a link that can be read, or one without anywhere to go. */
    Malformed,

    /** A place in the document that the document does not have. */
    OutsideDocument,
}

/**
 * Decides what a link does. It is a security rule, so it lives here, with tests, rather than in a
 * tap handler: only `http`, `https`, `mailto` and `tel` are ever followed, and the web ones are
 * shown by their real host.
 *
 * A PDF link's visible text need not match where it goes ("mybank.com" can lead anywhere), and a
 * URL can dress one host up as another (`https://mybank.com@elsewhere.com` goes to
 * `elsewhere.com`). What the reader is shown is worked out here, from the target itself.
 */
class ResolveLinkUseCase {

    /** @param pageCount The document's pages, to catch an internal link that points past them. */
    operator fun invoke(link: PageLink, pageCount: Int): LinkAction =
        when (link) {
            is PageLink.Internal ->
                if (link.pageIndex in 0 until pageCount) {
                    LinkAction.GoTo(link.pageIndex, link.position)
                } else {
                    LinkAction.Blocked("page ${link.pageIndex + 1}", BlockReason.OutsideDocument)
                }
            // Browsers drop tabs and line breaks anywhere in a URL (WHATWG): so does this, or the
            // host shown could differ from the one opened.
            is PageLink.External -> external(link.uri.trim().filterNot { it in "\t\n\r" })
        }

    private fun external(target: String): LinkAction {
        val colon = target.indexOf(':')
        val scheme = target.substring(0, colon.coerceAtLeast(0)).lowercase()
        return when {
            // A bare address, as some documents store them: taken as the secure web.
            colon < 0 && target.startsWith("www.", ignoreCase = true) -> web("https://$target")
            !scheme.isValidScheme() -> LinkAction.Blocked(target, BlockReason.Malformed)
            scheme == "http" || scheme == "https" -> web(target)
            scheme == "mailto" -> email(target, target.substring(colon + 1))
            scheme == "tel" -> dial(target, target.substring(colon + 1))
            else -> LinkAction.Blocked(target, BlockReason.Scheme)
        }
    }

    private fun web(url: String): LinkAction {
        val host = hostOf(url) ?: return LinkAction.Blocked(url, BlockReason.Malformed)
        return LinkAction.OpenWeb(
            url = url,
            host = host,
            secure = url.startsWith("https:", ignoreCase = true),
        )
    }

    /**
     * The host, read by hand rather than by a URI parser: those reject an internationalized name
     * outright, and it is exactly the case to show.
     */
    private fun hostOf(url: String): String? {
        val afterScheme = url.substringAfter("://", missingDelimiterValue = "")
        // A backslash ends the host as a slash does: browsers read it so for the web (WHATWG), and
        // "https://mybank.com\@elsewhere.com" goes to mybank.com.
        val authority = afterScheme.takeWhile { it !in "/\\?#" }
        // Everything up to the last "@" is who, not where.
        val hostAndPort = authority.substringAfterLast('@')
        val host =
            if (hostAndPort.startsWith("[")) {
                // An IPv6 address, kept whole.
                hostAndPort.substringBefore(']') + "]"
            } else {
                hostAndPort.substringBefore(':')
            }
        val name = host.trimEnd('.').lowercase()
        if (name.isEmpty() || name.any { it.isWhitespace() }) return null
        if (name.startsWith("[")) return name
        return runCatching { IDN.toASCII(name, IDN.ALLOW_UNASSIGNED) }
            .getOrNull()
            ?.takeIf {
                it.isNotEmpty()
            }
    }

    private fun email(uri: String, rest: String): LinkAction {
        val address = decode(rest.substringBefore('?')).trim()
        return if (address.contains('@')) LinkAction.ComposeEmail(uri, address)
        else LinkAction.Blocked(uri, BlockReason.Malformed)
    }

    private fun dial(uri: String, rest: String): LinkAction {
        // "tel:+34 600 000 000;ext=1" — the number, without parameters.
        val number = decode(rest.substringBefore(';')).trim()
        return if (number.any { it.isDigit() }) LinkAction.Dial(uri, number)
        else LinkAction.Blocked(uri, BlockReason.Malformed)
    }

    private fun decode(text: String): String = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            URLDecoder.decode(text.replace("+", "%2B"), StandardCharsets.UTF_8)
        } else {
            // For versions lower than TIRAMISU, use the deprecated method
            URLDecoder.decode(text.replace("+", "%2B"), "UTF-8")
        }
    }
        .getOrDefault(text)

    /** RFC 3986: a letter, then letters, digits, `+`, `-` or `.`. */
    private fun String.isValidScheme(): Boolean =
        isNotEmpty() && first().isLetter() && all { it.isLetterOrDigit() || it in "+-." }
}
