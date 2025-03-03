package com.bobbyesp.docucraft

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.bobbyesp.docucraft.core.presentation.common.Route
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.pdfScannerRouting

@Composable
fun Navigator(
    navHostController: NavHostController,
) {
    NavHost(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        navController = navHostController,
        startDestination = Route.DocucraftNavigator
    ) {
        pdfScannerRouting()

    }
}