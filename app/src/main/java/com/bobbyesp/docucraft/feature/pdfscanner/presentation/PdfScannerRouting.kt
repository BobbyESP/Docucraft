package com.bobbyesp.docucraft.feature.pdfscanner.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.navigation
import com.bobbyesp.docucraft.core.presentation.common.Route
import com.bobbyesp.docucraft.core.presentation.motion.animatedComposable
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.pages.home.HomePageWrapper

fun NavGraphBuilder.pdfScannerRouting() {
    navigation<Route.DocucraftNavigator>(
        startDestination = Route.DocucraftNavigator.Home
    ) {
        animatedComposable<Route.DocucraftNavigator.Home> {
            HomePageWrapper()
        }
    }
}