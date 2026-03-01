package com.composepdf.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.composepdf.state.FitMode

/**
 * Composable for displaying a single PDF page.
 *
 * The visual size of the page depends on [fitMode]:
 * - [FitMode.WIDTH] → always fills the full viewport width (standard reader mode).
 * - [FitMode.HEIGHT] → always fills the full viewport height.
 * - [FitMode.BOTH] → fills the viewport on the constraining axis (letterboxed).
 * - [FitMode.PROPORTIONAL] → fills [widthFraction] of the viewport width so that
 *   pages narrower than the widest page in the document appear proportionally smaller.
 *
 * @param bitmap The rendered page bitmap, or null if not yet rendered
 * @param pageIndex The zero-based page index (for accessibility)
 * @param aspectRatio The page aspect ratio (width / height)
 * @param isLoading Whether the page is currently being rendered
 * @param showLoadingIndicator Whether to show a loading spinner
 * @param fitMode How the page should be sized within the viewport
 * @param widthFraction Only used with [FitMode.PROPORTIONAL]: fraction of the viewport
 *   width this page should occupy (0f–1f). Defaults to 1f (full width).
 * @param modifier Modifier for the page container
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PdfPage(
    bitmap: Bitmap?,
    pageIndex: Int,
    aspectRatio: Float,
    isLoading: Boolean,
    showLoadingIndicator: Boolean,
    fitMode: FitMode = FitMode.WIDTH,
    widthFraction: Float = 1f,
    modifier: Modifier = Modifier
) {
    // Build the sizing modifier based on the active FitMode.
    //
    // FitMode.WIDTH / default → fillMaxWidth + aspectRatio (height follows)
    // FitMode.HEIGHT          → fillMaxHeight + aspectRatio (width follows)
    // FitMode.BOTH            → fillMaxSize + ContentScale.Fit handles letterboxing
    // FitMode.PROPORTIONAL    → fillMaxWidth(widthFraction) + aspectRatio
    //
    // In every case aspectRatio() is applied so Compose knows the height to reserve.
    val sizeModifier: Modifier = when (fitMode) {
        FitMode.WIDTH -> Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio.coerceAtLeast(0.1f))

        FitMode.HEIGHT -> Modifier
            .fillMaxHeight()
            .aspectRatio(aspectRatio.coerceAtLeast(0.1f), matchHeightConstraintsFirst = true)

        FitMode.BOTH -> Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio.coerceAtLeast(0.1f))

        FitMode.PROPORTIONAL -> Modifier
            .fillMaxWidth(widthFraction.coerceIn(0.01f, 1f))
            .aspectRatio(aspectRatio.coerceAtLeast(0.1f))
    }

    Box(
        modifier = modifier
            .then(sizeModifier)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null && !bitmap.isRecycled) {
            val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

            Image(
                bitmap = imageBitmap,
                contentDescription = "PDF page ${pageIndex + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else if (isLoading && showLoadingIndicator) {
            CircularWavyProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Composable for displaying the page loading placeholder.
 *
 * @param aspectRatio The page aspect ratio
 * @param modifier Modifier for the placeholder
 */
@Composable
internal fun PagePlaceholder(
    aspectRatio: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio.coerceAtLeast(0.1f))
            .background(Color(0xFFF5F5F5)),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 4.dp
        )
    }
}




