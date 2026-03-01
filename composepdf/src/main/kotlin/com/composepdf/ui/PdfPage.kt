package com.composepdf.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/**
 * Composable for displaying a single PDF page.
 * 
 * This composable handles the display of a rendered PDF page bitmap,
 * including loading state indication when the bitmap is not yet available.
 * 
 * @param bitmap The rendered page bitmap, or null if not yet rendered
 * @param pageIndex The zero-based page index (for accessibility)
 * @param aspectRatio The page aspect ratio (width / height)
 * @param isLoading Whether the page is currently being rendered
 * @param showLoadingIndicator Whether to show a loading spinner
 * @param modifier Modifier for the page container
 */
@Composable
internal fun PdfPage(
    bitmap: Bitmap?,
    pageIndex: Int,
    aspectRatio: Float,
    isLoading: Boolean,
    showLoadingIndicator: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio.coerceAtLeast(0.1f))
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null && !bitmap.isRecycled) {
            // Use remember to avoid recreating ImageBitmap on each recomposition
            val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
            
            Image(
                bitmap = imageBitmap,
                contentDescription = "PDF page ${pageIndex + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else if (isLoading && showLoadingIndicator) {
            // Show loading indicator
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp
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
