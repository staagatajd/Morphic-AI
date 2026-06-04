package com.golemprotocol.morphicai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import com.golemprotocol.morphicai.services.DatabaseService
import com.golemprotocol.morphicai.ui.auth.AuthScreen
import com.golemprotocol.morphicai.ui.auth.AuthViewModel
import com.golemprotocol.morphicai.ui.theme.MorphicAITheme
import com.golemprotocol.morphicai.utils.SessionManager

class LoginActivity : ComponentActivity() {
    private lateinit var dbService: DatabaseService
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dbService = DatabaseService(this)
        sessionManager = SessionManager(this)

        if (sessionManager.isLoggedIn()) {
            startMainActivity()
            return
        }

        setContent {
            val authViewModel = remember { AuthViewModel(dbService, sessionManager) }
            
            MorphicAITheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AuthScreen(
                        viewModel = authViewModel,
                        onAuthSuccess = {
                            startMainActivity()
                        }
                    )
                }
            }
        }
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        dbService.close()
    }
}
