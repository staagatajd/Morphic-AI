package com.golemprotocol.morphicai.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.golemprotocol.morphicai.models.AppSettings
import com.golemprotocol.morphicai.models.User
import com.golemprotocol.morphicai.services.DatabaseService
import com.golemprotocol.morphicai.services.Message
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
    val messages: List<Message> = emptyList(),
    val activeWorkspaceId: String = "first_workspace_uuid",
    val isLoading: Boolean = true,
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
            val messages = dbService.getMessagesByWorkspace(_uiState.value.activeWorkspaceId)
            
            _uiState.value = _uiState.value.copy(
                user = user,
                settings = settings,
                roleAnalytics = roleAnalytics,
                workspaces = workspaces,
                messages = messages
            )
            
            // Simulate content loading for skeletons
            delay(1000)
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    // 1. ACTIVE CONTEXT TRACKING
    fun selectWorkspace(workspaceId: String) {
        viewModelScope.launch {
            val messages = dbService.getMessagesByWorkspace(workspaceId)
            _uiState.value = _uiState.value.copy(
                activeWorkspaceId = workspaceId,
                messages = messages
            )
        }
    }

    fun sendMessage(text: String) {
        viewModelScope.launch {
            val workspaceId = _uiState.value.activeWorkspaceId
            
            // 2. STATEFUL PERSISTENCE - User Message
            if (dbService.insertMessage(workspaceId, text, true)) {
                _uiState.value = _uiState.value.copy(
                    messages = dbService.getMessagesByWorkspace(workspaceId)
                )
                
                delay(500)
                
                // 2. STATEFUL PERSISTENCE - AI Reply
                val reply = "I am currently unavailable at the moment. Please try again later."
                if (dbService.insertMessage(workspaceId, reply, false)) {
                    _uiState.value = _uiState.value.copy(
                        messages = dbService.getMessagesByWorkspace(workspaceId)
                    )
                }
            }
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

    fun createWorkspace(name: String) {
        viewModelScope.launch {
            val workspace = Workspace(
                id = java.util.UUID.randomUUID().toString(),
                name = name,
                createdAt = java.time.Instant.now().toString()
            )
            if (dbService.insertWorkspace(workspace)) {
                val updatedWorkspaces = dbService.getAllWorkspaces()
                _uiState.value = _uiState.value.copy(workspaces = updatedWorkspaces)
            }
        }
    }

    fun signOut() {
        sessionManager.clearSession()
    }
}
