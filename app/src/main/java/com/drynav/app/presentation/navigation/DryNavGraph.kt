package com.drynav.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.drynav.app.presentation.admin.AdminScreen
import com.drynav.app.presentation.auth.LoginScreen
import com.drynav.app.presentation.auth.OtpScreen
import com.drynav.app.presentation.auth.OtpSuccessScreen
import com.drynav.app.presentation.auth.RegisterScreen
import com.drynav.app.presentation.home.GetStartedScreen
import com.drynav.app.presentation.home.MainMenuScreen
import com.drynav.app.presentation.map.MapHomeScreen
import com.drynav.app.presentation.map.MapScreen
import com.drynav.app.presentation.map.MapViewModel
import com.drynav.app.presentation.onboarding.OnboardingScreen
import com.drynav.app.presentation.profile.ProfileScreen
import com.drynav.app.presentation.report.ReportFloodScreen
import com.drynav.app.presentation.report.ReportSuccessScreen
import com.drynav.app.presentation.splash.SplashScreen

@Composable
fun DryNavGraph() {
    val navController = rememberNavController()

    // Bottom-bar navigation: keep HOME as the base of the stack.
    fun navigateTab(route: String) {
        navController.navigate(route) {
            popUpTo(Routes.HOME) { inclusive = route == Routes.HOME }
            launchSingleTop = true
        }
    }

    // Entering the app (or leaving auth) resets the whole back stack.
    fun navigateClearingStack(route: String) {
        navController.navigate(route) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }

    // Auth screens hand us arbitrary routes; decide which need a stack reset.
    fun navigateFromAuth(route: String) = when (route) {
        Routes.GET_STARTED, Routes.HOME, Routes.LOGIN, Routes.OTP_SUCCESS ->
            navigateClearingStack(route)
        else -> navController.navigate(route)
    }

    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(onNavigate = ::navigateClearingStack)
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onFinished = { navigateClearingStack(Routes.LOGIN) })
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                onNavigate = ::navigateFromAuth,
                onGoToRegister = { navController.navigate(Routes.REGISTER) }
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(
                onNavigate = ::navigateFromAuth,
                onGoToLogin = { navController.popBackStack(Routes.LOGIN, inclusive = false) }
            )
        }
        composable(Routes.OTP) {
            OtpScreen(onNavigate = ::navigateFromAuth, onBack = { navController.popBackStack() })
        }
        composable(Routes.OTP_SUCCESS) {
            OtpSuccessScreen(onNavigate = ::navigateClearingStack)
        }
        composable(Routes.GET_STARTED) {
            GetStartedScreen(onGetStarted = { navigateClearingStack(Routes.HOME) })
        }
        composable(Routes.HOME) {
            MainMenuScreen(onNavigate = ::navigateTab)
        }
        // Maps1 (preview) and Maps2-6 (full map) share one MapViewModel scoped
        // to this nested graph, so a GPS fix acquired on one screen survives
        // the navigation to the other instead of restarting from scratch.
        navigation(startDestination = Routes.MAP_HOME, route = Routes.MAP_GRAPH) {
            composable(Routes.MAP_HOME) { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(Routes.MAP_GRAPH)
                }
                val mapViewModel: MapViewModel = hiltViewModel(parentEntry)
                MapHomeScreen(
                    viewModel = mapViewModel,
                    onNavigate = { route ->
                        if (route == Routes.MAP) navController.navigate(Routes.MAP)
                        else navigateTab(route)
                    }
                )
            }
            composable(Routes.MAP) { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(Routes.MAP_GRAPH)
                }
                val mapViewModel: MapViewModel = hiltViewModel(parentEntry)
                MapScreen(viewModel = mapViewModel, onBack = { navController.popBackStack() })
            }
        }
        composable(Routes.REPORT) {
            ReportFloodScreen(
                onNavigate = { route ->
                    if (route == Routes.REPORT_SUCCESS) navController.navigate(route)
                    else navigateTab(route)
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.REPORT_SUCCESS) {
            ReportSuccessScreen(
                onNavigate = ::navigateTab,
                onGoBack = { navigateTab(Routes.HOME) }
            )
        }
        composable(Routes.PROFILE) {
            ProfileScreen(
                onNavigate = ::navigateTab,
                onBack = { navController.popBackStack() },
                onLoggedOut = { navigateClearingStack(Routes.LOGIN) }
            )
        }
        composable(Routes.ADMIN) {
            AdminScreen(onNavigate = ::navigateTab)
        }
    }
}
