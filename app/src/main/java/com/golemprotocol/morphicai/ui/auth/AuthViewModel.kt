package com.golemprotocol.morphicai.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.golemprotocol.morphicai.models.User
import com.golemprotocol.morphicai.services.ApiClient
import com.golemprotocol.morphicai.services.DatabaseService
import com.golemprotocol.morphicai.services.SignInRequest
import com.golemprotocol.morphicai.services.SignUpRequest
import com.golemprotocol.morphicai.utils.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false,
    val isSignUpMode: Boolean = false
)

class AuthViewModel(
    private val dbService: DatabaseService,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState

    fun toggleMode() {
        _uiState.value = _uiState.value.copy(isSignUpMode = !_uiState.value.isSignUpMode, error = null)
    }

    fun signIn(email: String, pword: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = ApiClient.signIn(SignInRequest(email, pword))
                if (response.success == 1) {
                    val user = User(
                        id = response.accessToken ?: "",
                        username = response.username ?: "User",
                        email = email,
                        role = response.role ?: "user",
                        createdAt = Instant.now().toString()
                    )
                    dbService.upsertUser(user)
                    sessionManager.saveSession(user, response.accessToken ?: "")
                    _uiState.value = _uiState.value.copy(isLoading = false, isSuccess = true)
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = response.message)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Network error")
            }
        }
    }

    fun signUp(username: String, email: String, pword: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = ApiClient.signUp(SignUpRequest(username, email, pword))
                if (response.success == 1) {
                    _uiState.value = _uiState.value.copy(isLoading = false, isSignUpMode = false, error = "Account created. Please sign in.")
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = response.message)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Network error")
            }
        }
    }
}
