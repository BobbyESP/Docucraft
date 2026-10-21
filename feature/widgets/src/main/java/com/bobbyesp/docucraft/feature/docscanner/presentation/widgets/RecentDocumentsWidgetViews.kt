/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.widgets

import android.content.Context
import android.graphics.Bitmap
import android.text.format.DateUtils
import android.view.View
import android.widget.RemoteViews
import com.bobbyesp.docucraft.core.designsystem.R as DesignsystemR
import com.bobbyesp.docucraft.feature.docscanner.domain.widgets.WidgetDocument
import com.bobbyesp.docucraft.feature.widgets.R

/**
 * What the recent-documents widget sends the launcher for the documents it has room for.
 *
 * Apart from [RecentDocumentsWidgetRenderer], which reads the library, so that it can be inflated
 * in a test as a launcher would with nothing but a list of documents.
 */
internal class RecentDocumentsWidgetViews(private val context: Context) {

    /** @param previews The first page of each document that has one ready, by its uuid. */
    fun of(documents: List<WidgetDocument>, previews: Map<String, Bitmap>): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_recents).apply {
            // Said from here, and not left to the layout: the launcher reads a layout's strings in
            // the language of the system, and the rows are written in the app's.
            setTextViewText(
                R.id.widget_recents_title,
                context.getString(DesignsystemR.string.recents),
            )
            setTextViewText(
                R.id.widget_recents_empty,
                context.getString(R.string.widget_recents_empty),
            )
            setContentDescription(
                R.id.widget_recents_search,
                context.getString(DesignsystemR.string.search_documents),
            )
            setContentDescription(
                R.id.widget_recents_scan,
                context.getString(R.string.widget_scan_title),
            )

            setOnClickPendingIntent(R.id.widget_recents_title, WidgetIntents.openApp(context))
            setOnClickPendingIntent(R.id.widget_recents_search, WidgetIntents.search(context))
            setOnClickPendingIntent(R.id.widget_recents_scan, WidgetIntents.scan(context))

            val empty = documents.isEmpty()
            setViewVisibility(R.id.widget_recents_empty, if (empty) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.widget_recents_rows, if (empty) View.GONE else View.VISIBLE)

            // The launcher applies these views over the ones it already has, so the rows of the
            // last time are taken out before this time's are added.
            removeAllViews(R.id.widget_recents_rows)
            documents.forEach { document ->
                addView(R.id.widget_recents_rows, row(document, previews[document.uuid]))
            }
        }

    private fun row(document: WidgetDocument, preview: Bitmap?): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_recents_row).apply {
            setTextViewText(R.id.widget_row_title, document.name)
            setTextViewText(R.id.widget_row_subtitle, subtitleOf(document))
            if (preview != null) setImageViewBitmap(R.id.widget_row_thumbnail, preview)
            setOnClickPendingIntent(
                R.id.widget_row,
                WidgetIntents.openDocument(context, document.uuid),
            )
        }

    /**
     * How long the document is and the day it was last used. A date, not "2 hours ago": the widget
     * is drawn when the library changes, and words that depend on the time would go on saying what
     * was true then.
     */
    private fun subtitleOf(document: WidgetDocument): String {
        val date =
            DateUtils.formatDateTime(
                context,
                document.lastUsedAtEpochMillis,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH,
            )
        val pages =
            document.pageCount?.let {
                context.resources.getQuantityString(DesignsystemR.plurals.doc_n_pages, it, it)
            }
        return listOfNotNull(pages, date).joinToString(SEPARATOR)
    }

    private companion object {
        const val SEPARATOR = " · "
    }
}
