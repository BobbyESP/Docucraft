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
 * Android's Canvas refuses to draw bitmaps larger than ~100 MP; we stay well below
 * that by capping width at 4096 px (≈ 4K horizontal resolution).
 *
 * At zoom = 1 the bitmap is `viewport × quality` pixels (e.g. 1080 × 1.5 = 1620 px
 * for a crisp rendering on a 1080 p screen).
 * At high zoom the bitmap grows proportionally but never exceeds the cap, so the
 * render is still sharp for the visible area while memory stays bounded.
 */
class PageRenderer(
    private val bitmapPool: BitmapPool
) {

    private val nightModePaint = Paint().apply {
        colorFilter = ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    -1f,  0f,  0f, 0f, 255f,
                     0f, -1f,  0f, 0f, 255f,
                     0f,  0f, -1f, 0f, 255f,
                     0f,  0f,  0f, 1f,   0f
                )
            )
        )
    }

    /**
     * @property zoomLevel        Current viewer zoom (1.0 = fit-width).
     * @property renderQuality    Base oversampling at zoom = 1 (1.5 = 50 % more pixels).
     *                            At higher zooms this is scaled down automatically so the
     *                            bitmap never exceeds [MAX_BITMAP_PX] wide.
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

        Log.d(TAG, "render  page=${page.index}  " +
                "pdf=${page.width}×${page.height}pt  " +
                "viewport=${config.viewportWidthPx}px  " +
                "zoom=${config.zoomLevel}  quality=${config.renderQuality}  " +
                "→ bitmap=${width}×${height}px  " +
                "(${width * height * 4 / 1_048_576} MB)")

        val bitmap = bitmapPool.get(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(config.backgroundColor)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

        return if (config.nightMode) applyNightMode(bitmap) else bitmap
    }

    fun calculateRenderSize(
        pageWidth: Int,
        pageHeight: Int,
        config: RenderConfig
    ): Pair<Int, Int> = targetSize(pageWidth, pageHeight, config)

    // ── Private ───────────────────────────────────────────────────────────────

    private fun targetSize(pdfW: Int, pdfH: Int, config: RenderConfig): Pair<Int, Int> {
        val baseW = if (config.viewportWidthPx > 0f) {
            // Desired width = viewport × zoom × quality, capped at MAX_BITMAP_PX.
            val desired = config.viewportWidthPx * config.zoomLevel * config.renderQuality
            min(desired, MAX_BITMAP_PX.toFloat()).toInt().coerceAtLeast(1)
        } else {
            // Fallback for unit tests / zero viewport.
            val desired = pdfW * config.zoomLevel * config.renderQuality
            min(desired, MAX_BITMAP_PX.toFloat()).toInt().coerceAtLeast(1)
        }
        val h = (baseW * pdfH.toFloat() / pdfW.toFloat()).toInt().coerceAtLeast(1)
        return baseW to h
    }

    private fun applyNightMode(source: Bitmap): Bitmap {
        val cfg    = source.config ?: Bitmap.Config.ARGB_8888
        val result = bitmapPool.get(source.width, source.height, cfg)
        Canvas(result).drawBitmap(source, 0f, 0f, nightModePaint)
        bitmapPool.put(source)
        return result
    }

    companion object {
        /**
         * Maximum bitmap width in pixels.
         *
         * Android's Canvas hard-limit is ~100 MP total pixels. For a typical A4
         * aspect ratio (1:√2) a 4096-px wide bitmap is ~23 MP — well within budget
         * and sharp enough for any consumer display at any zoom level.
         *
         * Memory per bitmap at this size: 4096 × 5792 × 4 bytes ≈ 95 MB.
         * In practice most pages are narrower than landscape A4 so memory is lower.
         *
         * Adjust down (e.g. 2048) if the target devices are memory-constrained.
         */
        const val MAX_BITMAP_PX = 2048
    }
}
