/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.text.format.DateUtils
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.size.Scale
import coil.transform.RoundedCornersTransformation
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.feature.docscanner.domain.storage.DocumentThumbnails
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.KeepWidgetsCurrentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveWidgetDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.widgets.WidgetDocument
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/**
 * Draws the recent-documents widget: reads what it lists, and sends the launcher the views.
 *
 * Apart from its receiver so that the receiver stays what the system needs of it, a class with no
 * arguments, and this can be given what it reads with.
 */
class RecentDocumentsWidgetRenderer(
    private val context: Context,
    private val observeWidgetDocuments: ObserveWidgetDocumentsUseCase,
    private val thumbnails: DocumentThumbnails,
    private val imageLoader: ImageLoader,
) {

    suspend fun render(appWidgetIds: IntArray) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val sizes = appWidgetIds.associateWith { id ->
            WidgetSizes.of(manager.getAppWidgetOptions(id), fallback = PLACED_SIZE)
        }
        val mostRows = sizes.values.flatMap { it.all }.maxOfOrNull(::rowsFor) ?: return

        val documents = observeWidgetDocuments(limit = mostRows).first()
        val previews = previewsOf(documents)

        sizes.forEach { (id, widgetSizes) ->
            manager.updateAppWidget(
                id,
                widgetSizes.views { size -> views(documents.take(rowsFor(size)), previews) },
            )
        }
    }

    private fun rowsFor(size: SizeF): Int =
        RecentWidgetLayout.rowsFor(size.height, most = KeepWidgetsCurrentUseCase.WIDGET_DOCUMENTS)

    private fun views(documents: List<WidgetDocument>, previews: Map<String, Bitmap>): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_recents).apply {
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
                context.resources.getQuantityString(R.plurals.doc_n_pages, it, it)
            }
        return listOfNotNull(pages, date).joinToString(SEPARATOR)
    }

    /**
     * The first page of each document, small and already rounded, since the launcher draws a bitmap
     * as it is given. One that is not ready in time is left out, and its row shows the placeholder:
     * a preview may have to be drawn from the document first, and the system gives a receiver only
     * a few seconds.
     */
    private suspend fun previewsOf(documents: List<WidgetDocument>): Map<String, Bitmap> {
        val density = context.resources.displayMetrics.density
        val width = (PREVIEW_WIDTH_DP * density).toInt()
        val height = (PREVIEW_HEIGHT_DP * density).toInt()
        val previews = mutableMapOf<String, Bitmap>()

        withTimeoutOrNull(PREVIEWS_BUDGET_MILLIS.milliseconds) {
            documents.forEach { document ->
                val file = thumbnails.get(document.thumbnail)?.value ?: return@forEach
                val request =
                    ImageRequest.Builder(context)
                        .data(File(file))
                        .size(width, height)
                        .scale(Scale.FILL)
                        .transformations(RoundedCornersTransformation(PREVIEW_CORNER_DP * density))
                        // A bitmap that lives in the graphics memory of this process cannot be sent
                        // to another.
                        .allowHardware(false)
                        .build()
                imageLoader.execute(request).drawable?.toBitmap()?.let {
                    previews[document.uuid] = it
                }
            }
        }
        return previews
    }

    private companion object {
        /** Four cells by two, which is how the widget is placed. */
        val PLACED_SIZE = SizeF(300f, 180f)

        const val PREVIEW_WIDTH_DP = 30
        const val PREVIEW_HEIGHT_DP = 40
        const val PREVIEW_CORNER_DP = 6f
        const val PREVIEWS_BUDGET_MILLIS = 4_000L
        const val SEPARATOR = " · "
    }
}
