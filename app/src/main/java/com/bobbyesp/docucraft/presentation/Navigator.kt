package com.bobbyesp.docucraft.presentation

import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import com.bobbyesp.docucraft.presentation.common.LocalDrawerState
import com.bobbyesp.docucraft.presentation.common.LocalNavController
import com.bobbyesp.docucraft.presentation.common.LocalSnackbarHostState
import com.bobbyesp.docucraft.presentation.common.Route
import com.bobbyesp.docucraft.presentation.pages.HomePage
import com.bobbyesp.docucraft.presentation.pages.HomePageViewModel
import com.bobbyesp.ui.motion.animatedComposable

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Navigator() {
    val navController = LocalNavController.current

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRootRoute = rememberSaveable(navBackStackEntry, key = "currentRootRoute") {
        mutableStateOf(
            navBackStackEntry?.destination?.parent?.route ?: Route.DocucraftNavigator.route
        )
    }
    val currentRoute = rememberSaveable(navBackStackEntry, key = "currentRoute") {
        mutableStateOf(
            navBackStackEntry?.destination?.route ?: Route.DocucraftNavigator.Home.route
        )
    }

    val snackbarHostState = LocalSnackbarHostState.current

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState
            ) { dataReceived ->
                Snackbar(
                    modifier = Modifier,
                    snackbarData = dataReceived,
                    containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    ) {
        NavHost(
            modifier = Modifier
                .fillMaxSize(),
            navController = navController,
            startDestination = Route.DocucraftNavigator.route,
            route = Route.MainHost.route,
        ) {
            navigation(
                startDestination = Route.DocucraftNavigator.Home.route,
                route = Route.DocucraftNavigator.route
            ) {
                animatedComposable(Route.DocucraftNavigator.Home.route) {
                    val homeVm = hiltViewModel<HomePageViewModel>()
                    HomePage(viewModel = homeVm)
                }
            }
        }
    }
}