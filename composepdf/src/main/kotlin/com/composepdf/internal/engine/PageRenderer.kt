/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.internal.engine

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer

/**
 * Low-level rasterization of pages and tiles into pooled [Bitmap]s. Pixel production only; all
 * visual effects (night mode, scaling) happen at draw time in the UI.
 */
internal class PageRenderer(private val pool: BitmapPool) {

    /** Renders the whole page into a bitmap of exactly [targetWidth] x [targetHeight]. */
    fun renderBasePage(page: PdfRenderer.Page, targetWidth: Int, targetHeight: Int): Bitmap {
        val bitmap = pool.get(targetWidth, targetHeight)
        bitmap.eraseColor(Color.WHITE)
        val scale = targetWidth.toFloat() / page.width.toFloat()
        val matrix = Matrix().apply { postScale(scale, scale) }
        page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }

    /**
     * Renders one tile. [rect] is in level-space pixels, i.e. the page rasterized at `baseWidth *
     * levelScale` px wide.
     */
    fun renderTile(
        page: PdfRenderer.Page,
        rect: TileRect,
        levelScale: Float,
        baseWidth: Float,
    ): Bitmap {
        val bitmap = pool.get(rect.width, rect.height)
        bitmap.eraseColor(Color.WHITE)
        val scale = (baseWidth / page.width.toFloat()) * levelScale
        val matrix =
            Matrix().apply {
                postScale(scale, scale)
                postTranslate(-rect.left.toFloat(), -rect.top.toFloat())
            }
        page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }
}
