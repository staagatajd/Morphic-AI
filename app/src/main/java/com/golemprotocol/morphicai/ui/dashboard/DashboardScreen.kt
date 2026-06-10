package com.golemprotocol.morphicai.ui.dashboard

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onSignOut: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    Scaffold(
        bottomBar = {
            BottomNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                0 -> ChatScreen(
                    username = uiState.user?.username ?: "Guest",
                    workspaceName = uiState.workspaces.find { it.id == uiState.activeWorkspaceId }?.name ?: "Default",
                    messages = uiState.messages,
                    onSendMessage = viewModel::sendMessage
                )
                1 -> RolesScreen(
                    roleAnalytics = uiState.roleAnalytics,
                    onRoleSelected = viewModel::switchRole
                )
                2 -> WorkspaceScreen(
                    workspaces = uiState.workspaces,
                    onCreateWorkspace = viewModel::createWorkspace,
                    onWorkspaceSelected = { id ->
                        viewModel.selectWorkspace(id)
                        selectedTab = 0 // Navigate to Chat tab
                    }
                )
                3 -> MorphicScreen(
                    user = uiState.user,
                    settings = uiState.settings,
                    roleAnalytics = uiState.roleAnalytics,
                    workspaces = uiState.workspaces,
                    onLargeTextChange = viewModel::updateLargeText,
                    onAlwaysOnChange = { enabled ->
                        viewModel.updateAlwaysOn(enabled)
                        val msg = if (enabled) "Background optimization service activated." else "Service deactivated."
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    },
                    onSignOut = {
                        viewModel.signOut()
                        Toast.makeText(context, "Successfully signed out", Toast.LENGTH_SHORT).show()
                        onSignOut()
                    }
                )
            }
        }
    }
}

@Composable
fun BottomNavigationBar(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        BottomNavItem(
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            icon = Icons.Default.Email,
            label = "Chat"
        )
        BottomNavItem(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            icon = Icons.Default.Face,
            label = "Roles"
        )
        BottomNavItem(
            selected = selectedTab == 2,
            onClick = { onTabSelected(2) },
            icon = Icons.AutoMirrored.Filled.List,
            label = "Workspace"
        )
        BottomNavItem(
            selected = selectedTab == 3,
            onClick = { onTabSelected(3) },
            icon = Icons.Default.Settings,
            label = "Morphic"
        )
    }
}

@Composable
fun RowScope.BottomNavItem(selected: Boolean, onClick: () -> Unit, icon: ImageVector, label: String) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        )
    )
}
