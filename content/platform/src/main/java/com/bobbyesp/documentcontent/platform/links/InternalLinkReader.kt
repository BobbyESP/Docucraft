/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.documentcontent.platform.links

import com.bobbyesp.documentcontent.NormalizedPoint
import com.bobbyesp.documentcontent.NormalizedRect
import com.bobbyesp.documentcontent.PageLink

/**
 * The links of a PDF that lead to another place in it, read from the file itself.
 *
 * The platform reports none: `getGotoLinks()` comes back empty for every form of internal link (see
 * `PlatformContentTest`), so a table of contents could be seen but not followed. This reads each
 * page's `/Link` annotations (ISO 32000-1 §12.5.6.5) and where they lead:
 * - an explicit destination, `[page /XYZ left top zoom]` and the other forms of §12.3.2.2;
 * - a named one, looked up in the catalog's `/Dests` or in its `/Names` tree, which is how LaTeX
 *   and most word processors write them;
 * - a `/GoTo` action, or a `/Named` action to the next, previous, first or last page.
 *
 * Everything is placed as the platform places what it reports: over the page as displayed, with its
 * crop box and rotation applied, in `[0,1]²` from the top left. The target page's geometry comes
 * from the page tree, so where on it a link leads is known without opening that page.
 *
 * Web links are not read here: the platform reports those.
 *
 * Not thread-safe; the content session reads one page at a time.
 */
internal class InternalLinkReader(bytes: PdfBytes) {

    private val objects = PdfObjects(bytes)

    private class PageNode(val number: Int?, val dict: PdfDict, val geometry: PageGeometry)

    private val pages: List<PageNode> by lazy { readPageTree() }

    private val pageIndexByObject: Map<Int, Int> by lazy {
        HashMap<Int, Int>().apply {
            pages.forEachIndexed { index, page -> page.number?.let { putIfAbsent(it, index) } }
        }
    }

    val pageCount: Int
        get() = pages.size

    /** The internal links of page [index], in the order the page lists them. */
    fun links(index: Int): List<PageLink.Internal> {
        val page = pages.getOrNull(index) ?: return emptyList()
        val annotations = objects.array(page.dict["Annots"]) ?: return emptyList()
        return annotations.mapNotNull { annotation ->
            val dict = objects.dict(annotation) ?: return@mapNotNull null
            if (dict.name("Subtype") != "Link") return@mapNotNull null
            val target = target(dict, index) ?: return@mapNotNull null
            val bounds =
                bounds(dict, page.geometry).ifEmpty {
                    return@mapNotNull null
                }
            PageLink.Internal(
                bounds = bounds,
                pageIndex = target.page,
                position = target.position?.let { pages[target.page].geometry.point(it) },
            )
        }
    }

    // ------------------------------------------------------------------------------- the pages

    /**
     * The pages in order, each with the boxes and rotation it inherits (§7.7.3.4), walked without
     * recursion and without visiting a node twice: a damaged tree can loop.
     */
    private fun readPageTree(): List<PageNode> {
        val root = objects.catalog?.get("Pages") ?: return emptyList()
        val result = ArrayList<PageNode>()
        val visited = HashSet<Int>()

        class Pending(val node: Any?, val inherited: Inherited)
        val stack = ArrayDeque<Pending>()
        stack.addLast(Pending(root, Inherited()))
        while (stack.isNotEmpty() && result.size < MAX_PAGES) {
            val pending = stack.removeLast()
            val number = (pending.node as? PdfRef)?.number
            if (number != null && !visited.add(number)) continue
            val dict = objects.dict(pending.node) ?: continue
            val inherited = pending.inherited.with(dict)
            val kids = objects.array(dict["Kids"])
            if (kids != null && dict.name("Type") != "Page") {
                // Reversed onto the stack, so the first kid comes off first.
                for (kid in kids.asReversed()) stack.addLast(Pending(kid, inherited))
            } else if (dict.name("Type") != "Pages") {
                result += PageNode(number, dict, inherited.geometry())
            }
        }
        return result
    }

    private inner class Inherited(
        val mediaBox: PdfBox? = null,
        val cropBox: PdfBox? = null,
        val rotate: Int = 0,
    ) {
        fun with(dict: PdfDict) =
            Inherited(
                mediaBox = box(dict["MediaBox"]) ?: mediaBox,
                cropBox = box(dict["CropBox"]) ?: cropBox,
                rotate = objects.number(dict["Rotate"])?.toInt() ?: rotate,
            )

        /** As pdfium sizes a page: the crop box within the media box, US Letter if neither. */
        fun geometry(): PageGeometry {
            val media = mediaBox ?: PdfBox(0.0, 0.0, 612.0, 792.0)
            val visible = cropBox?.intersect(media)?.takeIf { !it.isEmpty } ?: media
            return PageGeometry(visible, Math.floorMod(rotate, 360) / 90 * 90)
        }
    }

    private fun box(value: Any?): PdfBox? {
        val numbers = objects.array(value)?.map { objects.number(it) ?: return null } ?: return null
        if (numbers.size != 4) return null
        return PdfBox(
            left = minOf(numbers[0], numbers[2]),
            bottom = minOf(numbers[1], numbers[3]),
            right = maxOf(numbers[0], numbers[2]),
            top = maxOf(numbers[1], numbers[3]),
        )
    }

    // -------------------------------------------------------------------------- the link's area

    /**
     * One box per quadrilateral when the link lists them (`/QuadPoints`, a link over two lines),
     * otherwise its `/Rect`.
     */
    private fun bounds(annotation: PdfDict, geometry: PageGeometry): List<NormalizedRect> {
        val quads = objects.array(annotation["QuadPoints"])?.map { objects.number(it) }
        if (quads != null && quads.size >= 8 && quads.size % 8 == 0 && quads.all { it != null }) {
            val fromQuads =
                quads.chunked(8).mapNotNull { quad ->
                    val xs = quad.filterIndexed { i, _ -> i % 2 == 0 }.map { it!! }
                    val ys = quad.filterIndexed { i, _ -> i % 2 == 1 }.map { it!! }
                    geometry.rect(PdfBox(xs.min(), ys.min(), xs.max(), ys.max()))
                }
            if (fromQuads.isNotEmpty()) return fromQuads
        }
        return listOfNotNull(box(annotation["Rect"])?.let(geometry::rect))
    }

    // ---------------------------------------------------------------------------- where it leads

    private class Target(val page: Int, val position: PdfPosition?)

    private fun target(annotation: PdfDict, fromPage: Int): Target? {
        annotation["Dest"]?.let {
            return destination(it, depth = 0)
        }
        val action = objects.dict(annotation["A"]) ?: return null
        return when (action.name("S")) {
            "GoTo" -> destination(action["D"], depth = 0)
            "Named" ->
                when (objects.resolve(action["N"]).let { (it as? PdfName)?.value }) {
                        "NextPage" -> fromPage + 1
                        "PrevPage" -> fromPage - 1
                        "FirstPage" -> 0
                        "LastPage" -> pages.lastIndex
                        else -> null
                    }
                    ?.takeIf { it in pages.indices }
                    ?.let { Target(it, null) }
            else -> null
        }
    }

    private fun destination(value: Any?, depth: Int): Target? {
        if (depth > MAX_DESTINATION_DEPTH) return null
        return when (val resolved = objects.resolve(value)) {
            is List<*> -> explicit(resolved)
            // The value of a named destination may be wrapped: << /D [...] >>.
            is PdfDict -> destination(resolved["D"], depth + 1)
            is PdfName -> named(resolved.value)?.let { destination(it, depth + 1) }
            // An encrypted string cannot be read, and would match no name.
            is PdfString ->
                if (objects.encrypted) null
                else
                    named(resolved.key)?.let {
                        destination(it, depth + 1)
                    }
            else -> null
        }
    }

    private fun explicit(array: List<*>): Target? {
        val page =
            when (val first = array.firstOrNull()) {
                is PdfRef -> pageIndexByObject[first.number]
                // A page number, as a remote destination has it; some writers use it locally too.
                else -> first.asInt()
            } ?: return null
        if (page !in pages.indices) return null
        fun at(index: Int) = objects.number(array.getOrNull(index))
        val position =
            when ((objects.resolve(array.getOrNull(1)) as? PdfName)?.value) {
                "XYZ" -> PdfPosition(x = at(2), y = at(3))
                "FitH",
                "FitBH" -> PdfPosition(x = null, y = at(2))
                "FitV",
                "FitBV" -> PdfPosition(x = at(2), y = null)
                "FitR" -> PdfPosition(x = at(2), y = at(5))
                else -> null
            }?.takeIf { it.x != null || it.y != null }
        return Target(page, position)
    }

    private val namedDestinations: PdfDict? by lazy { objects.dict(objects.catalog?.get("Dests")) }

    private val destinationTree: PdfDict? by lazy {
        objects.dict(objects.dict(objects.catalog?.get("Names"))?.get("Dests"))
    }

    private fun named(name: String): Any? =
        namedDestinations?.get(name) ?: destinationTree?.let { lookUp(it, name) }

    /** [key] in a name tree (§7.9.6), by its `/Limits` where it has them, every kid where not. */
    private fun lookUp(root: PdfDict, key: String): Any? {
        val visited = HashSet<PdfDict>()
        val stack = ArrayDeque<Pair<PdfDict, Int>>()
        stack.addLast(root to 0)
        while (stack.isNotEmpty()) {
            val (node, depth) = stack.removeLast()
            if (!visited.add(node) || depth > MAX_TREE_DEPTH) continue
            objects.array(node["Names"])?.let { names ->
                for (i in 0 until names.size / 2) {
                    if (keyOf(names[i * 2]) == key) return names[i * 2 + 1]
                }
            }
            val kids = objects.array(node["Kids"]) ?: continue
            for (kid in kids.asReversed()) {
                val child = objects.dict(kid) ?: continue
                val limits = objects.array(child["Limits"])?.map { keyOf(it) }
                val low = limits?.getOrNull(0)
                val high = limits?.getOrNull(1)
                if (low != null && high != null && (key < low || key > high)) continue
                stack.addLast(child to depth + 1)
            }
        }
        return null
    }

    private fun keyOf(value: Any?): String? =
        when (val resolved = objects.resolve(value)) {
            is PdfString -> resolved.key
            is PdfName -> resolved.value
            else -> null
        }

    private companion object {
        const val MAX_PAGES = 100_000
        const val MAX_DESTINATION_DEPTH = 8
        const val MAX_TREE_DEPTH = 32
    }
}

/** A rectangle in a page's user space, in points, y upwards. */
internal data class PdfBox(
    val left: Double,
    val bottom: Double,
    val right: Double,
    val top: Double,
) {
    val isEmpty: Boolean
        get() = right <= left || top <= bottom

    fun intersect(other: PdfBox) =
        PdfBox(
            maxOf(left, other.left),
            maxOf(bottom, other.bottom),
            minOf(right, other.right),
            minOf(top, other.top),
        )
}

/** A point in user space; a side the destination leaves unchanged is `null`. */
internal data class PdfPosition(val x: Double?, val y: Double?)

/**
 * A page's visible box and rotation, and the way from its user space to the displayed page,
 * normalized: the same space the platform reports links in, and the viewer places everything in.
 *
 * @param rotate Clockwise, 0, 90, 180 or 270.
 */
internal class PageGeometry(private val box: PdfBox, private val rotate: Int) {

    fun rect(rect: PdfBox): NormalizedRect? {
        val corners =
            listOf(
                normalize(rect.left, rect.bottom),
                normalize(rect.right, rect.top),
                normalize(rect.left, rect.top),
                normalize(rect.right, rect.bottom),
            )
        val left = corners.minOf { it.first }.coerceIn(0f, 1f)
        val top = corners.minOf { it.second }.coerceIn(0f, 1f)
        val right = corners.maxOf { it.first }.coerceIn(0f, 1f)
        val bottom = corners.maxOf { it.second }.coerceIn(0f, 1f)
        return NormalizedRect(left, top, right, bottom).takeIf { right > left && bottom > top }
    }

    /** A side left unchanged reads as the page's own edge: its left, or its top. */
    fun point(position: PdfPosition): NormalizedPoint {
        val (x, y) = normalize(position.x ?: box.left, position.y ?: box.top)
        return NormalizedPoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
    }

    private fun normalize(x: Double, y: Double): Pair<Float, Float> {
        val width = box.right - box.left
        val height = box.top - box.bottom
        if (width <= 0.0 || height <= 0.0) return 0f to 0f
        val u = ((x - box.left) / width).toFloat()
        val v = ((box.top - y) / height).toFloat()
        // Each quarter turn clockwise moves the top left corner to the top right.
        return when (rotate) {
            90 -> 1f - v to u
            180 -> 1f - u to 1f - v
            270 -> v to 1f - u
            else -> u to v
        }
    }
}
