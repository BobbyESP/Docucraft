package com.bobbyesp.docucraft

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.bobbyesp.docucraft.core.presentation.common.Route

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
        navigation<Route.DocucraftNavigator>(
            startDestination = Route.DocucraftNavigator.Home
        ) {
            composable<Route.DocucraftNavigator.Home> {

            }
        }

    }
}