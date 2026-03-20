package com.composepdf.internal.service.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import com.composepdf.internal.service.cache.bitmap.BitmapPool

class PageRenderer(
    private val bitmapPool: BitmapPool
) {

    data class RenderConfig(
        val zoomLevel: Float = 1f,
        val renderQuality: Float = 1.0f,
        val backgroundColor: Int = Color.WHITE
    )

    suspend fun render(page: PdfRenderer.Page, baseWidth: Float, config: RenderConfig): Bitmap {
        val (width, height) = targetSize(page.width, page.height, baseWidth, config)
        val bitmap = bitmapPool.get(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(config.backgroundColor)

        val scale = width.toFloat() / page.width.toFloat()
        val matrix = Matrix().apply { postScale(scale, scale) }

        page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }

    suspend fun renderTile(
        page: PdfRenderer.Page,
        tileRect: Rect,
        zoom: Float,
        baseWidth: Float,
        backgroundColor: Int = Color.WHITE
    ): Bitmap {
        val bitmap = bitmapPool.get(tileRect.width(), tileRect.height(), Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(backgroundColor)

        val scaleFactor = (baseWidth / page.width.toFloat()) * zoom
        val matrix = Matrix().apply {
            postScale(scaleFactor, scaleFactor)
            postTranslate(-tileRect.left.toFloat(), -tileRect.top.toFloat())
        }

        page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }

    fun calculateRenderSize(
        pageWidth: Int, pageHeight: Int, baseWidth: Float, config: RenderConfig
    ): Pair<Int, Int> = targetSize(pageWidth, pageHeight, baseWidth, config)

    private fun targetSize(
        pdfW: Int, pdfH: Int, baseWidth: Float, config: RenderConfig
    ): Pair<Int, Int> {
        val aspectRatio = pdfH.toFloat() / pdfW.toFloat()
        val desiredW = if (baseWidth > 0f) {
            baseWidth * config.zoomLevel * config.renderQuality
        } else {
            pdfW * config.zoomLevel * config.renderQuality
        }

        var finalW = desiredW
        var finalH = finalW * aspectRatio

        if (finalW > MAX_BITMAP_PX) {
            finalW = MAX_BITMAP_PX.toFloat()
            finalH = finalW * aspectRatio
        }
        if (finalH > MAX_BITMAP_PX) {
            finalH = MAX_BITMAP_PX.toFloat()
            finalW = finalH / aspectRatio
        }

        return finalW.toInt().coerceAtLeast(1) to finalH.toInt().coerceAtLeast(1)
    }

    companion object {
        const val MAX_BITMAP_PX = 2048

        const val TILE_SIZE = 256
    }
}
