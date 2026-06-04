package com.golemprotocol.morphicai

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import com.golemprotocol.morphicai.services.DatabaseService
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

        if (!sessionManager.isLoggedIn()) {
            signOutUser()
            return
        }

        setContent {
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
                    DashboardScreen(
                        viewModel = dashboardViewModel,
                        onSignOut = {
                            signOutUser()
                        }
                    )
                }
            }
        }
    }

    private fun signOutUser() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        dbService.close()
    }
}
