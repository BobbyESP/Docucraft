/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.widgets

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.feature.docscanner.domain.model.DocumentThumbnail
import com.bobbyesp.docucraft.feature.docscanner.domain.widgets.WidgetDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The widgets' views, inflated as a launcher inflates them.
 *
 * A layout that uses a view or an attribute a launcher refuses compiles, installs, and shows "Can't
 * load widget" on the home screen. Only inflating it tells, and what a launcher accepts differs by
 * version, so these run on the oldest Android the app supports as well as a recent one.
 */
@RunWith(AndroidJUnit4::class)
class WidgetViewsTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val recents = RecentDocumentsWidgetViews(context)

    @Test
    fun everyScanLayoutInflatesAndTakesThePress() {
        ScanWidgetLayout.entries.forEach { layout ->
            val root = inflate(ScanWidgetViews.of(context, layout))

            // The launcher animates from this view when the press opens the app.
            assertEquals("$layout", android.R.id.background, root.id)
            assertTrue("$layout", root.hasOnClickListeners())
        }
    }

    @Test
    fun theScanWidgetSaysWhatItDoesInEveryLayout() {
        val title = context.getString(R.string.widget_scan_title)

        ScanWidgetLayout.entries.forEach { layout ->
            val root = inflate(ScanWidgetViews.of(context, layout))

            // The one with no room for words still has them for a screen reader.
            if (layout == ScanWidgetLayout.Small) {
                assertEquals(title, root.contentDescription.toString())
            } else {
                assertEquals("$layout", title, root.text(R.id.widget_scan_title))
            }
        }
    }

    @Test
    fun withNoDocumentTheRecentsWidgetSaysSoAndKeepsItsButtons() {
        val root = inflate(recents.of(emptyList(), emptyMap()))

        val empty = root.findViewById<TextView>(R.id.widget_recents_empty)
        assertEquals(View.VISIBLE, empty.visibility)
        assertEquals(context.getString(R.string.widget_recents_empty), empty.text.toString())
        assertEquals(View.GONE, root.findViewById<View>(R.id.widget_recents_rows).visibility)

        assertTrue(root.findViewById<View>(R.id.widget_recents_title).hasOnClickListeners())
        assertTrue(root.findViewById<View>(R.id.widget_recents_search).hasOnClickListeners())
        assertTrue(root.findViewById<View>(R.id.widget_recents_scan).hasOnClickListeners())
    }

    @Test
    fun theRecentsWidgetShowsARowPerDocumentInOrder() {
        val root =
            inflate(recents.of(listOf(document("a"), document("b"), document("c")), emptyMap()))

        val rows = root.findViewById<ViewGroup>(R.id.widget_recents_rows)
        assertEquals(View.VISIBLE, rows.visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.widget_recents_empty).visibility)
        assertEquals(
            listOf("Document a", "Document b", "Document c"),
            rows.children().map { it.text(R.id.widget_row_title) },
        )
        rows.children().forEach { assertTrue(it.hasOnClickListeners()) }
    }

    @Test
    fun aRowSaysHowLongTheDocumentIsOnceItsPagesAreCounted() {
        val root =
            inflate(
                recents.of(
                    listOf(document("a", pageCount = 3), document("b", pageCount = null)),
                    emptyMap(),
                )
            )

        val (counted, uncounted) =
            root.findViewById<ViewGroup>(R.id.widget_recents_rows).children().map {
                it.text(R.id.widget_row_subtitle)
            }
        val pages = context.resources.getQuantityString(R.plurals.doc_n_pages, 3, 3)
        assertTrue(counted, counted.startsWith("$pages · "))
        // Only the day it was last used: no separator with nothing before it.
        assertTrue(uncounted, uncounted.isNotBlank())
        assertFalse(uncounted, uncounted.contains("·"))
    }

    @Test
    fun aRowShowsThePreviewItIsGivenAndAPlaceholderOtherwise() {
        val preview = Bitmap.createBitmap(30, 40, Bitmap.Config.ARGB_8888)
        preview.eraseColor(Color.RED)
        val root = inflate(recents.of(listOf(document("a"), document("b")), mapOf("a" to preview)))

        val (with, without) =
            root.findViewById<ViewGroup>(R.id.widget_recents_rows).children().map {
                it.findViewById<ImageView>(R.id.widget_row_thumbnail).drawable
            }
        // A copy of it: the views carry their bitmaps to another process.
        assertTrue(preview.sameAs((with as BitmapDrawable).bitmap))
        assertFalse(without is BitmapDrawable)
    }

    @Test
    fun drawnAgainTheRecentsWidgetShowsOnlyTheNewRows() {
        // A launcher keeps the views it inflated and applies each update over them.
        val root =
            inflate(recents.of(listOf(document("a"), document("b"), document("c")), emptyMap()))

        instrumentation.runOnMainSync {
            recents.of(listOf(document("d")), emptyMap()).reapply(context, root)
        }

        val rows = root.findViewById<ViewGroup>(R.id.widget_recents_rows)
        assertEquals(listOf("Document d"), rows.children().map { it.text(R.id.widget_row_title) })
    }

    private fun inflate(views: RemoteViews): View {
        lateinit var root: View
        instrumentation.runOnMainSync { root = views.apply(context, FrameLayout(context)) }
        return root
    }

    private fun View.text(id: Int): String = findViewById<TextView>(id).text.toString()

    private fun ViewGroup.children(): List<View> = (0 until childCount).map(::getChildAt)

    private fun document(id: String, pageCount: Int? = 2) =
        WidgetDocument(
            uuid = id,
            name = "Document $id",
            pageCount = pageCount,
            lastUsedAtEpochMillis = 1_760_000_000_000,
            thumbnail = DocumentThumbnail(documentUuid = id, contentVersion = 1),
        )
}
