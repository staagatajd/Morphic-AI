package com.golemprotocol.morphicai

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.golemprotocol.morphicai.services.DatabaseService
import com.golemprotocol.morphicai.ui.auth.AuthScreen
import com.golemprotocol.morphicai.ui.auth.AuthViewModel
import com.golemprotocol.morphicai.ui.dashboard.DashboardScreen
import com.golemprotocol.morphicai.ui.dashboard.DashboardViewModel
import com.golemprotocol.morphicai.ui.theme.MorphicAITheme
import com.golemprotocol.morphicai.utils.SessionManager

class MainActivity : ComponentActivity() {
    private lateinit var dbService: DatabaseService
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dbService = DatabaseService(this)
        sessionManager = SessionManager(this)

        setContent {
            val navController = rememberNavController()
            val startDestination = if (sessionManager.isLoggedIn()) "dashboard" else "auth"
            
            // Shared ViewModels (simple implementation for this scope)
            val authViewModel = remember { AuthViewModel(dbService, sessionManager) }
            val dashboardViewModel = remember { DashboardViewModel(dbService, sessionManager) }
            
            val dashboardState by dashboardViewModel.uiState.collectAsState()
            
            // Handle Always On flag
            LaunchedEffect(dashboardState.settings.alwaysOn) {
                if (dashboardState.settings.alwaysOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            MorphicAITheme(largeText = dashboardState.settings.largeTexts) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    NavHost(navController = navController, startDestination = startDestination) {
                        composable("auth") {
                            AuthScreen(
                                viewModel = authViewModel,
                                onAuthSuccess = {
                                    navController.navigate("dashboard") {
                                        popUpTo("auth") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("dashboard") {
                            DashboardScreen(
                                viewModel = dashboardViewModel,
                                onSignOut = {
                                    navController.navigate("auth") {
                                        popUpTo("dashboard") { inclusive = true }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        dbService.close()
    }
}
