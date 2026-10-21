/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.widgets.presentation

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.util.SizeF
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.size.Scale
import coil.transform.RoundedCornersTransformation
import com.bobbyesp.docucraft.feature.widgets.domain.KeepWidgetsCurrentUseCase
import com.bobbyesp.docucraft.feature.widgets.domain.ObserveWidgetDocumentsUseCase
import com.bobbyesp.docucraft.feature.widgets.domain.WidgetDocument
import com.bobbyesp.docucraft.library.domain.storage.DocumentThumbnails
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

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

    private val views = RecentDocumentsWidgetViews(context)

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
                widgetSizes.views { size -> views.of(documents.take(rowsFor(size)), previews) },
            )
        }
    }

    private fun rowsFor(size: SizeF): Int =
        RecentWidgetLayout.rowsFor(size.height, most = KeepWidgetsCurrentUseCase.WIDGET_DOCUMENTS)

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
    }
}
