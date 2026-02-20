package com.evenai.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.evenai.companion.ui.navigation.G1NavGraph
import com.evenai.companion.ui.navigation.Screen
import com.evenai.companion.ui.theme.G1CompanionTheme
import com.evenai.companion.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            G1CompanionTheme {
                val viewModel   = hiltViewModel<MainViewModel>()
                val navController = rememberNavController()
                val onboardingDone by viewModel.onboardingDone.collectAsState()

                val startDest = if (onboardingDone) Screen.Dashboard.route
                               else Screen.Onboarding.route

                G1NavGraph(navController, startDest)
            }
        }
    }
}
