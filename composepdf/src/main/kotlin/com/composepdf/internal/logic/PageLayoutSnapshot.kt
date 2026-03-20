package com.composepdf.internal.logic

import android.util.Size
import com.composepdf.FitMode
import com.composepdf.ScrollDirection
import kotlin.math.min

/**
 * An immutable snapshot of the document layout calculated at a base zoom level of `1f`.
 *
 * This class serves as a pure, side-effect-free representation of the document geometry.
 * It is responsible for translating document coordinates to screen coordinates and performing
 * spatial queries such as visibility detection, hit testing, and pan clamping.
 *
 * The layout is recalculated and a new snapshot is emitted whenever the viewport dimensions,
 * page list, spacing, or [FitMode] change.
 *
 * @property pageSizes The original dimensions of the PDF pages.
 * @property pageOffsets The vertical or horizontal offsets (Y or X) of each page in document space.
 * @property pageHeights The calculated heights of each page after applying [FitMode].
 * @property pageWidths The calculated widths of each page after applying [FitMode].
 * @property totalDocumentSize The total height or width of the scrollable area, including spacing.
 * @property corridorBreadth The breadth of the document corridor (max height if horizontal, max width if vertical).
 * @property viewport The dimensions of the visible component area.
 * @property pageSpacingPx The gap between pages in pixels.
 * @property scrollDirection The direction in which pages are laid out.
 */
internal class PageLayoutSnapshot(
    val pageSizes: List<Size>,
    val pageOffsets: FloatArray,
    val pageHeights: FloatArray,
    val pageWidths: FloatArray,
    val totalDocumentSize: Float,
    val corridorBreadth: Float,
    val viewport: ViewportMetrics,
    val pageSpacingPx: Float,
    val scrollDirection: ScrollDirection
) {
    val isEmpty: Boolean get() = pageSizes.isEmpty() || !viewport.isReady

    fun pageHeightPx(index: Int): Float = pageHeights.getOrNull(index) ?: viewport.height

    fun pageWidthPx(index: Int): Float = pageWidths.getOrNull(index) ?: viewport.width

    fun pageTopDocY(index: Int): Float = if (scrollDirection == ScrollDirection.VERTICAL) {
        pageOffsets.getOrNull(index) ?: 0f
    } else 0f

    fun pageLeftDocX(index: Int): Float = if (scrollDirection == ScrollDirection.HORIZONTAL) {
        pageOffsets.getOrNull(index) ?: 0f
    } else 0f

    fun visiblePageIndices(panX: Float, panY: Float, zoom: Float): IntRange {
        if (isEmpty || zoom <= 0f) return IntRange.EMPTY

        val margin = pageSpacingPx * 0.5f

        return if (scrollDirection == ScrollDirection.VERTICAL) {
            val docTop = (-panY / zoom) - margin
            val docBottom = ((viewport.height - panY) / zoom) + margin
            val first = firstIntersectingPage(docTop)
            val last = lastIntersectingPage(docBottom)
            if (first == -1 || last == -1 || first > last) IntRange.EMPTY else first..last
        } else {
            val docLeft = (-panX / zoom) - margin
            val docRight = ((viewport.width - panX) / zoom) + margin
            val first = firstIntersectingPage(docLeft)
            val last = lastIntersectingPage(docRight)
            if (first == -1 || last == -1 || first > last) IntRange.EMPTY else first..last
        }
    }

    fun isPointOverPage(
        screenX: Float,
        screenY: Float,
        panX: Float,
        panY: Float,
        zoom: Float
    ): Boolean {
        if (isEmpty || zoom <= 0f) return false

        val docX = (screenX - panX) / zoom
        val docY = (screenY - panY) / zoom

        val pageIndex = if (scrollDirection == ScrollDirection.VERTICAL) {
            pageIndexAtDocumentOffset(docY)
        } else {
            pageIndexAtDocumentOffset(docX)
        }

        if (pageIndex == -1) return false

        val pageTop = pageTopDocY(pageIndex)
        val pageLeft = pageLeftDocX(pageIndex)
        val pageWidth = pageWidthPx(pageIndex)
        val pageHeight = pageHeightPx(pageIndex)

        return if (scrollDirection == ScrollDirection.VERTICAL) {
            val actualPageLeft = panX + (corridorBreadth - pageWidth) * zoom / 2f
            docY >= pageTop && docY <= pageTop + pageHeight &&
                    screenX >= actualPageLeft && screenX <= actualPageLeft + pageWidth * zoom
        } else {
            val actualPageTop = panY + (corridorBreadth - pageHeight) * zoom / 2f
            docX >= pageLeft && docX <= pageLeft + pageWidth &&
                    screenY >= actualPageTop && screenY <= actualPageTop + pageHeight * zoom
        }
    }

    fun clampPan(panX: Float, panY: Float, zoom: Float): PanPosition {
        if (!viewport.isReady) return PanPosition(panX, panY)

        val scaledWidth: Float
        val scaledHeight: Float

        if (scrollDirection == ScrollDirection.VERTICAL) {
            scaledWidth = corridorBreadth * zoom
            scaledHeight = totalDocumentSize * zoom
        } else {
            scaledWidth = totalDocumentSize * zoom
            scaledHeight = corridorBreadth * zoom
        }

        val clampedX = if (scaledWidth <= viewport.width) {
            (viewport.width - scaledWidth) / 2f
        } else {
            panX.coerceIn(-(scaledWidth - viewport.width), 0f)
        }

        val clampedY = if (scaledHeight <= viewport.height) {
            (viewport.height - scaledHeight) / 2f
        } else {
            panY.coerceIn(viewport.height - scaledHeight, 0f)
        }

        return PanPosition(clampedX, clampedY)
    }

    fun centeredPanForPage(pageIndex: Int, zoom: Float): PanPosition {
        val safeIndex = pageIndex.coerceIn(0, (pageSizes.size - 1).coerceAtLeast(0))
        val pageWidth = pageWidthPx(safeIndex)
        val pageHeight = pageHeightPx(safeIndex)
        val pageTop = pageTopDocY(safeIndex)
        val pageLeft = pageLeftDocX(safeIndex)

        return if (scrollDirection == ScrollDirection.VERTICAL) {
            val centeredPanY = (viewport.height / 2f) - (pageTop + pageHeight / 2f) * zoom
            val centeredPanX = (viewport.width / 2f) - (corridorBreadth * zoom / 2f)
            PanPosition(centeredPanX, centeredPanY)
        } else {
            val centeredPanX = (viewport.width / 2f) - (pageLeft + pageWidth / 2f) * zoom
            val centeredPanY = (viewport.height / 2f) - (corridorBreadth * zoom / 2f)
            PanPosition(centeredPanX, centeredPanY)
        }
    }

    fun fitDocumentZoom(fitMode: FitMode, minZoom: Float, maxZoom: Float): Float {
        if (isEmpty) return minZoom

        val docWidth =
            if (scrollDirection == ScrollDirection.VERTICAL) corridorBreadth else totalDocumentSize
        val docHeight =
            if (scrollDirection == ScrollDirection.VERTICAL) totalDocumentSize else corridorBreadth

        if (docWidth <= 0f || docHeight <= 0f) return minZoom

        val zoom = when (fitMode) {
            FitMode.WIDTH, FitMode.PROPORTIONAL -> viewport.width / docWidth
            FitMode.HEIGHT -> viewport.height / docHeight
            FitMode.BOTH -> min(viewport.width / docWidth, viewport.height / docHeight)
        }
        return zoom.coerceIn(minZoom, maxZoom)
    }

    fun fitPageZoom(pageIndex: Int, fitMode: FitMode, minZoom: Float, maxZoom: Float): Float {
        if (isEmpty) return minZoom

        val safeIndex = pageIndex.coerceIn(0, pageSizes.lastIndex)
        val baseWidth = pageWidthPx(safeIndex).takeIf { it > 0f } ?: return minZoom
        val baseHeight = pageHeightPx(safeIndex).takeIf { it > 0f } ?: return minZoom

        val zoom = when (fitMode) {
            FitMode.WIDTH, FitMode.PROPORTIONAL -> viewport.width / baseWidth
            FitMode.HEIGHT -> viewport.height / baseHeight
            FitMode.BOTH -> min(viewport.width / baseWidth, viewport.height / baseHeight)
        }
        return zoom.coerceIn(minZoom, maxZoom)
    }

    fun currentPageAtViewportCenter(panX: Float, panY: Float, zoom: Float): Int? {
        if (isEmpty || zoom <= 0f) return null

        val centerOffset = if (scrollDirection == ScrollDirection.VERTICAL) {
            (viewport.height / 2f - panY) / zoom
        } else {
            (viewport.width / 2f - panX) / zoom
        }

        val pageIndex = pageIndexAtDocumentOffset(centerOffset)
        return pageIndex.takeIf { it >= 0 }?.coerceIn(0, pageOffsets.lastIndex)
    }

    private fun firstIntersectingPage(offset: Float): Int {
        var low = 0
        var high = pageOffsets.lastIndex
        var first = -1

        while (low <= high) {
            val mid = (low + high) ushr 1
            if (pageOffsets[mid] + (if (scrollDirection == ScrollDirection.VERTICAL) pageHeights[mid] else pageWidths[mid]) >= offset) {
                first = mid
                high = mid - 1
            } else {
                low = mid + 1
            }
        }
        return first
    }

    private fun lastIntersectingPage(offset: Float): Int {
        var low = 0
        var high = pageOffsets.lastIndex
        var last = -1

        while (low <= high) {
            val mid = (low + high) ushr 1
            if (pageOffsets[mid] <= offset) {
                last = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return last
    }

    private fun pageIndexAtDocumentOffset(offset: Float): Int {
        var low = 0
        var high = pageOffsets.lastIndex
        var pageIndex = -1

        while (low <= high) {
            val mid = (low + high) ushr 1
            if (pageOffsets[mid] <= offset) {
                pageIndex = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return pageIndex
    }

    companion object {
        fun empty(viewport: ViewportMetrics = ViewportMetrics()): PageLayoutSnapshot =
            PageLayoutSnapshot(
                pageSizes = emptyList(),
                pageOffsets = FloatArray(0),
                pageHeights = FloatArray(0),
                pageWidths = FloatArray(0),
                totalDocumentSize = 0f,
                corridorBreadth = viewport.width,
                viewport = viewport,
                pageSpacingPx = 0f,
                scrollDirection = ScrollDirection.VERTICAL
            )

        fun build(
            pageSizes: List<Size>,
            viewportWidth: Float,
            viewportHeight: Float,
            fitMode: FitMode,
            pageSpacingPx: Float,
            scrollDirection: ScrollDirection
        ): PageLayoutSnapshot {
            val viewport = ViewportMetrics(viewportWidth, viewportHeight)
            if (pageSizes.isEmpty() || !viewport.isReady) return empty(viewport).copy(
                scrollDirection = scrollDirection
            )

            val count = pageSizes.size
            val pageOffsets = FloatArray(count)
            val pageHeights = FloatArray(count)
            val pageWidths = FloatArray(count)

            val maxPdfWidth = pageSizes.maxOfOrNull { it.width }?.toFloat() ?: 1f
            val maxPdfHeight = pageSizes.maxOfOrNull { it.height }?.toFloat() ?: 1f

            var currentOffset = 0f
            var maxBreadth = 0f

            for (index in 0 until count) {
                val pageSize = pageSizes[index]
                val pdfWidth = pageSize.width.toFloat()
                val pdfHeight = pageSize.height.toFloat()
                val aspectRatio = pdfWidth / pdfHeight

                val (baseWidth, baseHeight) = when (fitMode) {
                    FitMode.WIDTH -> viewport.width to (viewport.width / aspectRatio)
                    FitMode.HEIGHT -> (viewport.height * aspectRatio) to viewport.height
                    FitMode.BOTH -> {
                        val scale = min(viewport.width / pdfWidth, viewport.height / pdfHeight)
                        (pdfWidth * scale) to (pdfHeight * scale)
                    }

                    FitMode.PROPORTIONAL -> {
                        if (scrollDirection == ScrollDirection.VERTICAL) {
                            val scale = viewport.width / maxPdfWidth
                            (pdfWidth * scale) to (pdfHeight * scale)
                        } else {
                            val scale = viewport.height / maxPdfHeight
                            (pdfWidth * scale) to (pdfHeight * scale)
                        }
                    }
                }

                pageWidths[index] = baseWidth
                pageHeights[index] = baseHeight
                pageOffsets[index] = currentOffset

                if (scrollDirection == ScrollDirection.VERTICAL) {
                    currentOffset += baseHeight + pageSpacingPx
                    maxBreadth = maxOf(maxBreadth, baseWidth)
                } else {
                    currentOffset += baseWidth + pageSpacingPx
                    maxBreadth = maxOf(maxBreadth, baseHeight)
                }
            }

            return PageLayoutSnapshot(
                pageSizes = pageSizes,
                pageOffsets = pageOffsets,
                pageHeights = pageHeights,
                pageWidths = pageWidths,
                totalDocumentSize = (currentOffset - pageSpacingPx).coerceAtLeast(0f),
                corridorBreadth = maxBreadth,
                viewport = viewport,
                pageSpacingPx = pageSpacingPx,
                scrollDirection = scrollDirection
            )
        }
    }

    fun copy(
        pageSizes: List<Size> = this.pageSizes,
        pageOffsets: FloatArray = this.pageOffsets,
        pageHeights: FloatArray = this.pageHeights,
        pageWidths: FloatArray = this.pageWidths,
        totalDocumentSize: Float = this.totalDocumentSize,
        corridorBreadth: Float = this.corridorBreadth,
        viewport: ViewportMetrics = this.viewport,
        pageSpacingPx: Float = this.pageSpacingPx,
        scrollDirection: ScrollDirection = this.scrollDirection
    ) = PageLayoutSnapshot(
        pageSizes,
        pageOffsets,
        pageHeights,
        pageWidths,
        totalDocumentSize,
        corridorBreadth,
        viewport,
        pageSpacingPx,
        scrollDirection
    )
}

/** Viewport metrics used by layout and tile planning. */
internal data class ViewportMetrics(
    val width: Float = 0f,
    val height: Float = 0f
) {
    val isReady: Boolean get() = width > 0f && height > 0f
}

/** Simple value object for pan coordinates. */
internal data class PanPosition(
    val x: Float,
    val y: Float
)
