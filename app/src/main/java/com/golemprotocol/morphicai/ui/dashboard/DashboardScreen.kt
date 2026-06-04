package com.golemprotocol.morphicai.ui.dashboard

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.golemprotocol.morphicai.models.User
import kotlin.random.Random

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onSignOut: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showProfileDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            ProfileCard(
                user = uiState.user,
                onClick = { showProfileDialog = true }
            )
        },
        bottomBar = {
            BottomNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            if (uiState.isLoading) {
                SkeletonList()
            } else {
                DashboardContent()
            }
        }
    }

    if (showProfileDialog) {
        ProfileDialog(
            user = uiState.user,
            largeText = uiState.settings.largeTexts,
            alwaysOn = uiState.settings.alwaysOn,
            onLargeTextChange = viewModel::updateLargeText,
            onAlwaysOnChange = viewModel::updateAlwaysOn,
            onSignOut = {
                viewModel.signOut()
                showProfileDialog = false
                onSignOut()
            },
            onDismiss = { showProfileDialog = false }
        )
    }
}

@Composable
fun ProfileCard(user: User?, onClick: () -> Unit) {
    val avatarColor = remember(user?.username) {
        val hash = user?.username?.hashCode() ?: 0
        Color(
            red = (hash and 0xFF0000 shr 16) / 255f,
            green = (hash and 0x00FF00 shr 8) / 255f,
            blue = (hash and 0x0000FF) / 255f,
            alpha = 1f
        ).copy(alpha = 0.6f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(avatarColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = user?.username?.take(1)?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = user?.username ?: "Guest",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Dashboard",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SkeletonList() {
    LazyColumn(modifier = Modifier.padding(16.dp)) {
        items(3) {
            SkeletonItem()
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun SkeletonItem() {
    val infiniteTransition = rememberInfiniteTransition(label = "")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = ""
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.LightGray.copy(alpha = alpha))
            .padding(16.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth(0.4f).height(20.dp).background(Color.Gray.copy(alpha = 0.3f)))
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth().height(14.dp).background(Color.Gray.copy(alpha = 0.2f)))
        Spacer(modifier = Modifier.height(4.dp))
        Box(modifier = Modifier.fillMaxWidth(0.8f).height(14.dp).background(Color.Gray.copy(alpha = 0.2f)))
    }
}

@Composable
fun DashboardContent() {
    LazyColumn(modifier = Modifier.padding(16.dp)) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Adaptive Tasks", style = MaterialTheme.typography.titleLarge)
                    Text("System is optimizing your morning routine.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Persona Context Metrics", style = MaterialTheme.typography.titleLarge)
                    Text("98% alignment with your usual productivity cycle.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Routines Sync Monitor", style = MaterialTheme.typography.titleLarge)
                    Text("All systems operational. Next sync in 45m.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun BottomNavigationBar(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    NavigationBar(containerColor = Color.White) {
        BottomNavItem(
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            icon = Icons.Default.Home,
            label = "Home"
        )
        BottomNavItem(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            icon = Icons.Default.Search,
            label = "Search"
        )
        BottomNavItem(
            selected = selectedTab == 2,
            onClick = { onTabSelected(2) },
            icon = Icons.Default.LocationOn,
            label = "Map"
        )
        BottomNavItem(
            selected = selectedTab == 3,
            onClick = { onTabSelected(3) },
            icon = Icons.Default.Settings,
            label = "Settings"
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
            unselectedIconColor = Color.Gray,
            unselectedTextColor = Color.Gray,
            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        )
    )
}

@Composable
fun ProfileDialog(
    user: User?,
    largeText: Boolean,
    alwaysOn: Boolean,
    onLargeTextChange: (Boolean) -> Unit,
    onAlwaysOnChange: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user?.username?.take(1)?.uppercase() ?: "?",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(text = user?.username ?: "Guest", style = MaterialTheme.typography.titleLarge)
                Text(text = user?.email ?: "", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Large texts", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = largeText, onCheckedChange = onLargeTextChange)
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Always on", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = alwaysOn, onCheckedChange = onAlwaysOnChange)
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                TextButton(onClick = onSignOut) {
                    Text("Sign out", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
