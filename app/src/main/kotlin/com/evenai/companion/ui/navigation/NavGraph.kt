package com.evenai.companion.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.evenai.companion.ui.dashboard.DashboardScreen
import com.evenai.companion.ui.developer.DeveloperScreen
import com.evenai.companion.ui.onboarding.OnboardingScreen
import com.evenai.companion.ui.reconnect.ResyncScreen
import com.evenai.companion.ui.viewmodel.MainViewModel

sealed class Screen(val route: String) {
    object Onboarding  : Screen("onboarding")
    object Dashboard   : Screen("dashboard")
    object Resync      : Screen("resync")
    object Developer   : Screen("developer")
}

@Composable
fun G1NavGraph(navController: NavHostController, startDest: String) {
    NavHost(navController = navController, startDestination = startDest) {

        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onResyncNeeded = { navController.navigate(Screen.Resync.route) },
                onDeveloperMode = { navController.navigate(Screen.Developer.route) }
            )
        }

        composable(Screen.Resync.route) {
            ResyncScreen(
                onDone = { navController.popBackStack() }
            )
        }

        composable(Screen.Developer.route) {
            DeveloperScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
