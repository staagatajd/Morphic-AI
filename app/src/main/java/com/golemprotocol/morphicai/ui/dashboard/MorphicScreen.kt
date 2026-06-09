package com.golemprotocol.morphicai.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.golemprotocol.morphicai.models.AppSettings
import com.golemprotocol.morphicai.models.User
import com.golemprotocol.morphicai.services.RoleAnalytics
import com.golemprotocol.morphicai.services.Workspace

@Composable
fun MorphicScreen(
    user: User?,
    settings: AppSettings,
    roleAnalytics: RoleAnalytics?,
    workspaces: List<Workspace>,
    onLargeTextChange: (Boolean) -> Unit,
    onAlwaysOnChange: (Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Profile Header
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = user?.username?.take(1)?.uppercase() ?: "?",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = user?.username ?: "Guest",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = user?.email ?: "No email associated",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // System Anchors
        SectionHeader("System Anchors")
        InfoRow("Current Role", roleAnalytics?.currentRole ?: "None")
        InfoRow("Active Workspace", workspaces.firstOrNull()?.name ?: "Default")
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Adaptation Insights
        SectionHeader("Adaptation Insights")
        InfoRow("Most used role", roleAnalytics?.mostUsedRole ?: "None")
        InfoRow("Role switches", (roleAnalytics?.roleSwitchesCount ?: 0).toString())
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Settings Configurations
        SectionHeader("Settings")
        ToggleRow("Large Texts", settings.largeTexts, onLargeTextChange)
        ToggleRow("Keep Screen On", settings.alwaysOn, onAlwaysOnChange)
        
        Spacer(modifier = Modifier.height(40.dp))
        
        // Sign Out Button
        Button(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("Sign Out", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    )
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
