package com.composepdf.renderer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.util.Log
import com.composepdf.cache.BitmapPool
import kotlin.math.min

private const val TAG = "PdfPageRenderer"

class PageRenderer(
    private val bitmapPool: BitmapPool
) {

    private val nightModePaint = Paint().apply {
        colorFilter = ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }

    data class RenderConfig(
        val zoomLevel: Float = 1f,
        val renderQuality: Float = 1.0f,
        val viewportWidthPx: Float = 0f,
        val nightMode: Boolean = false,
        val backgroundColor: Int = android.graphics.Color.WHITE
    )

    fun render(page: PdfRenderer.Page, config: RenderConfig): Bitmap {
        val (width, height) = targetSize(page.width, page.height, config)
        val bitmap = bitmapPool.get(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(config.backgroundColor)
        
        val scale = width.toFloat() / page.width.toFloat()
        val matrix = Matrix().apply { postScale(scale, scale) }
        
        page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }

    fun renderTile(
        page: PdfRenderer.Page,
        tileRect: Rect,
        zoom: Float,
        viewportWidth: Float,
        backgroundColor: Int = android.graphics.Color.WHITE
    ): Bitmap {
        // Usar un tamaño de bitmap que coincida exactamente con el Rect solicitado
        val bitmap = bitmapPool.get(tileRect.width(), tileRect.height(), Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(backgroundColor)

        val scaleFactor = (viewportWidth / page.width.toFloat()) * zoom
        val matrix = Matrix()
        matrix.postScale(scaleFactor, scaleFactor)
        matrix.postTranslate(-tileRect.left.toFloat(), -tileRect.top.toFloat())

        page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }

    fun calculateRenderSize(
        pageWidth: Int,
        pageHeight: Int,
        config: RenderConfig
    ): Pair<Int, Int> = targetSize(pageWidth, pageHeight, config)

    private fun targetSize(pdfW: Int, pdfH: Int, config: RenderConfig): Pair<Int, Int> {
        val aspectRatio = pdfH.toFloat() / pdfW.toFloat()
        val desiredW = if (config.viewportWidthPx > 0f) {
            config.viewportWidthPx * config.zoomLevel * config.renderQuality
        } else {
            pdfW * config.zoomLevel * config.renderQuality
        }

        val desiredH = desiredW * aspectRatio
        var finalW = desiredW
        var finalH = desiredH

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
        // Reducimos a 512 para que cada trozo se procese mucho más rápido
        const val TILE_SIZE = 512
    }
}
