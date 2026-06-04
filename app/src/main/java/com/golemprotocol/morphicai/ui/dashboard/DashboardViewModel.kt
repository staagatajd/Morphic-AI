package com.golemprotocol.morphicai.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.golemprotocol.morphicai.models.AppSettings
import com.golemprotocol.morphicai.models.User
import com.golemprotocol.morphicai.services.DatabaseService
import com.golemprotocol.morphicai.utils.SessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DashboardUiState(
    val user: User? = null,
    val settings: AppSettings = AppSettings(),
    val isLoading: Boolean = true
)

class DashboardViewModel(
    private val dbService: DatabaseService,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val user = sessionManager.getUser()
            val settings = dbService.getAppSettings()
            _uiState.value = _uiState.value.copy(user = user, settings = settings)
            
            // Simulate content loading for skeletons
            delay(2000)
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun updateLargeText(enabled: Boolean) {
        viewModelScope.launch {
            val newSettings = _uiState.value.settings.copy(largeTexts = enabled)
            dbService.updateAppSettings(newSettings)
            _uiState.value = _uiState.value.copy(settings = newSettings)
        }
    }

    fun updateAlwaysOn(enabled: Boolean) {
        viewModelScope.launch {
            val newSettings = _uiState.value.settings.copy(alwaysOn = enabled)
            dbService.updateAppSettings(newSettings)
            _uiState.value = _uiState.value.copy(settings = newSettings)
        }
    }

    fun signOut() {
        sessionManager.clearSession()
    }
}
