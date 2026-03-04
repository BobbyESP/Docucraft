package com.composepdf.renderer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.util.Log
import com.composepdf.cache.BitmapPool
import kotlin.math.min

private const val TAG = "PdfPageRenderer"

/**
 * Renders PDF pages to bitmaps sized in **screen pixels**.
 *
 * ## Sizing model
 *
 *   bitmapWidth = clamp(viewportPx × zoom × quality, minPx, MAX_BITMAP_PX)
 *
 * The cap [MAX_BITMAP_PX] prevents OOM / "bitmap too large" crashes at high zoom.
 * Android's Canvas refuses to draw bitmaps larger than a certain threshold (often 100MB or texture limits).
 * We cap both dimensions to ensure the bitmap stays within safe limits while maintaining aspect ratio.
 */
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

    /**
     * @property zoomLevel        Current viewer zoom (1.0 = fit-width).
     * @property renderQuality    Base oversampling at zoom = 1 (1.5 = 50 % more pixels).
     *                            At higher zooms this is scaled down automatically so the
     *                            bitmap never exceeds [MAX_BITMAP_PX].
     * @property viewportWidthPx  Viewport width in physical screen pixels.
     * @property nightMode        Invert colours for dark mode.
     * @property backgroundColor  Page background colour.
     */
    data class RenderConfig(
        val zoomLevel: Float = 1f,
        val renderQuality: Float = 1.5f,
        val viewportWidthPx: Float = 0f,
        val nightMode: Boolean = false,
        val backgroundColor: Int = android.graphics.Color.WHITE
    )

    fun render(page: PdfRenderer.Page, config: RenderConfig): Bitmap {
        val (width, height) = targetSize(page.width, page.height, config)

        Log.d(
            TAG, "render  page=${page.index}  " +
                    "pdf=${page.width}×${page.height}pt  " +
                    "viewport=${config.viewportWidthPx}px  " +
                    "zoom=${config.zoomLevel}  quality=${config.renderQuality}  " +
                    "→ bitmap=${width}×${height}px  " +
                    "(${width * height * 4 / 1_048_576} MB)"
        )

        val bitmap = bitmapPool.get(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(config.backgroundColor)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

        return bitmap
    }

    fun calculateRenderSize(
        pageWidth: Int,
        pageHeight: Int,
        config: RenderConfig
    ): Pair<Int, Int> = targetSize(pageWidth, pageHeight, config)

    // ── Private ───────────────────────────────────────────────────────────────

    private fun targetSize(pdfW: Int, pdfH: Int, config: RenderConfig): Pair<Int, Int> {
        val aspectRatio = pdfH.toFloat() / pdfW.toFloat()

        // 1. Determine desired width based on viewport and zoom
        val desiredW = if (config.viewportWidthPx > 0f) {
            config.viewportWidthPx * config.zoomLevel * config.renderQuality
        } else {
            pdfW * config.zoomLevel * config.renderQuality
        }

        val desiredH = desiredW * aspectRatio

        var finalW = desiredW
        var finalH = desiredH

        // 2. Cap dimensions while preserving aspect ratio.
        // Capping both ensures we don't exceed Hardware Canvas limits (typically 4096 or 8192)
        // or total byte size limits (e.g. 100MB).
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

    private fun applyNightMode(source: Bitmap): Bitmap {
        val cfg = source.config ?: Bitmap.Config.ARGB_8888
        val result = bitmapPool.get(source.width, source.height, cfg)
        Canvas(result).drawBitmap(source, 0f, 0f, nightModePaint)
        bitmapPool.put(source)
        return result
    }

    companion object {
        /**
         * Maximum bitmap dimension in pixels.
         *
         * Android's Canvas has limits on bitmap size. A 4096x4096px ARGB_8888 bitmap
         * takes 64MB, which is safe for most devices. Some devices/Android versions
         * crash if a single draw operation exceeds 100MB or if a dimension exceeds
         * the OpenGL texture limit (often 4096 or 8192).
         */
        const val MAX_BITMAP_PX = 4096
    }
}
