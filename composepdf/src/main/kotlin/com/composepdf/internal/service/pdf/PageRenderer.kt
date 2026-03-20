package com.composepdf.internal.service.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import com.composepdf.internal.service.cache.bitmap.BitmapPool

/**
 * Handles the low-level rasterization of PDF pages and individual tiles into [Bitmap] instances.
 *
 * This class serves as a wrapper around [PdfRenderer.Page], utilizing a [BitmapPool] to
 * minimize memory allocations during high-frequency rendering tasks. It focuses strictly
 * on pixel production; visual transformations (such as Night Mode filters) are expected
 * to be applied at the UI or Shader layer to maintain high performance.
 *
 * @property bitmapPool The pool used to acquire and recycle bitmaps for rendering.
 */
class PageRenderer(
    private val bitmapPool: BitmapPool
) {

    /**
     * Configuration parameters for rendering a PDF page.
     *
     * @property zoomLevel The magnification factor to apply to the page.
     * @property renderQuality A multiplier for the render resolution. Higher values result in
     * sharper images but consume more memory and processing power.
     * @property backgroundColor The [android.graphics.Color] used to fill the bitmap background
     * before the PDF content is drawn.
     */
    data class RenderConfig(
        val zoomLevel: Float = 1f,
        val renderQuality: Float = 1.0f,
        val backgroundColor: Int = Color.WHITE
    )

    /**
     * Renders a full PDF page into a [Bitmap] using the provided [RenderConfig].
     *
     * This method calculates the target dimensions based on the page's aspect ratio,
     * requested base width, and zoom levels, then retrieves a bitmap from the pool
     * to perform the rasterization.
     *
     * @param page The [PdfRenderer.Page] to be rasterized.
     * @param baseWidth The reference width of the page at 1.0x zoom.
     * @param config Parameters for rendering, including zoom level, quality, and background color.
     * @return A [Bitmap] containing the rendered page content.
     */
    suspend fun render(page: PdfRenderer.Page, baseWidth: Float, config: RenderConfig): Bitmap {
        val (width, height) = targetSize(page.width, page.height, baseWidth, config)
        val bitmap = bitmapPool.get(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(config.backgroundColor)

        val scale = width.toFloat() / page.width.toFloat()
        val matrix = Matrix().apply { postScale(scale, scale) }

        page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }

    /**
     * Renders a specific rectangular tile of a page at high resolution.
     *
     * @param page The PDF page to render.
     * @param tileRect The coordinates and dimensions of the tile to render (in scaled pixels).
     * @param zoom The current zoom level.
     * @param baseWidth The width the page should have at zoom 1.0.
     * @param backgroundColor The color used to clear the bitmap before rendering.
     * @return A [Bitmap] from the pool containing the rendered tile content.
     */
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

    /**
     * Calculates the pixel dimensions for a page based on its base width and zoom.
     */
    fun calculateRenderSize(
        pageWidth: Int,
        pageHeight: Int,
        baseWidth: Float,
        config: RenderConfig
    ): Pair<Int, Int> = targetSize(pageWidth, pageHeight, baseWidth, config)

    private fun targetSize(
        pdfW: Int,
        pdfH: Int,
        baseWidth: Float,
        config: RenderConfig
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
