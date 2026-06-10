package com.golemprotocol.morphicai.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.golemprotocol.morphicai.services.Workspace

@Composable
fun WorkspaceScreen(
    workspaces: List<Workspace>,
    onCreateWorkspace: (String) -> Unit,
    onWorkspaceSelected: (String) -> Unit
) {
    // 2. WORKSPACE SEARCH FILTER STATE
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(value = false) }

    // Filtered list based on search query
    val filteredWorkspaces = remember(workspaces, searchQuery) {
        workspaces.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Search and Add Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search Workspace") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Directory Column
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (filteredWorkspaces.isEmpty() && searchQuery.isEmpty()) {
                // Initial placeholder state if no real data
                items(listOf("Personal Workspace", "Team Project Alpha")) { name ->
                    WorkspaceRow(
                        workspace = Workspace(id = "placeholder", name = name, createdAt = ""),
                        onClick = {}
                    )
                }
            } else {
                items(filteredWorkspaces, key = { it.id }) { workspace ->
                    // 4. INTERACTIVE ROUTING
                    WorkspaceRow(
                        workspace = workspace,
                        onClick = { onWorkspaceSelected(workspace.id) }
                    )
                }
            }
        }
    }

    // 1. WORKSPACE ADDITION DIALOG
    if (showAddDialog) {
        var newWorkspaceName by remember { mutableStateOf("") }
        var isError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("New Workspace") },
            text = {
                Column {
                    Text(
                        "Enter a name for your new workspace environment.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    OutlinedTextField(
                        value = newWorkspaceName,
                        onValueChange = { 
                            newWorkspaceName = it
                            if (it.isNotBlank()) isError = false
                        },
                        label = { Text("Workspace Name") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = isError,
                        singleLine = true,
                        supportingText = {
                            if (isError) {
                                Text("Name cannot be blank", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newWorkspaceName.isNotBlank()) {
                            onCreateWorkspace(newWorkspaceName)
                            showAddDialog = false
                        } else {
                            isError = true
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun WorkspaceRow(workspace: Workspace, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.List,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                text = workspace.name,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
