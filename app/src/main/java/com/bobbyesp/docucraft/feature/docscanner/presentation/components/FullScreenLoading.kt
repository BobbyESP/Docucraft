package com.bobbyesp.docucraft.feature.docscanner.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FullScreenLoading(
    modifier: Modifier = Modifier,
    scrimColor: Color = Color.Black.copy(alpha = 0.6f)
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scrimColor)
            .clickable(enabled = false, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        CircularWavyProgressIndicator()
    }
}