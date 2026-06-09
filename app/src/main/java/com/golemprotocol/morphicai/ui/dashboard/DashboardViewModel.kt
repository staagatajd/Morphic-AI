package com.golemprotocol.morphicai.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.golemprotocol.morphicai.models.AppSettings
import com.golemprotocol.morphicai.models.User
import com.golemprotocol.morphicai.services.DatabaseService
import com.golemprotocol.morphicai.services.RoleAnalytics
import com.golemprotocol.morphicai.services.Workspace
import com.golemprotocol.morphicai.utils.SessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DashboardUiState(
    val user: User? = null,
    val settings: AppSettings = AppSettings(),
    val roleAnalytics: RoleAnalytics? = null,
    val workspaces: List<Workspace> = emptyList(),
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
            val roleAnalytics = dbService.getRoleAnalytics()
            val workspaces = dbService.getAllWorkspaces()
            _uiState.value = _uiState.value.copy(
                user = user,
                settings = settings,
                roleAnalytics = roleAnalytics,
                workspaces = workspaces
            )
            
            // Simulate content loading for skeletons
            delay(1000)
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun switchRole(newRole: String) {
        viewModelScope.launch {
            dbService.switchRole(newRole)
            val updatedAnalytics = dbService.getRoleAnalytics()
            _uiState.value = _uiState.value.copy(roleAnalytics = updatedAnalytics)
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
