package com.example.vasganchalenge1.ui.mcp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpServerScreen(
    state: McpServerUiState,
    onBack: () -> Unit,
    onServerUrlChange: (String) -> Unit,
    onConnectRemote: () -> Unit,
    onConnectGithubLocal: () -> Unit,
    onConnectUtilityLocal: () -> Unit,
    onToggleServer: (String) -> Unit,
    onCallGithubGetUser: () -> Unit,
    onCallGithubGetRepo: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MCP - Servers") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = state.serverUrl,
                onValueChange = onServerUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Remote Server URL") }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onConnectRemote,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Connect remote")
                }
                Button(
                    onClick = onConnectGithubLocal,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Connect github")
                }
                Button(
                    onClick = onConnectUtilityLocal,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Connect utility")
                }
            }

            Text("Servers", style = MaterialTheme.typography.titleMedium)
            if (state.servers.isEmpty()) {
                Text("No servers registered")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.servers, key = { it.serverId }) { server ->
                        Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 1.dp) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("${server.label} (${server.serverId})")
                                Text("Status: ${server.status}")
                                Text("URL: ${server.url.ifBlank { "—" }}")
                                Text("Tools: ${server.toolsCount}")
                                server.error?.let {
                                    Text(
                                        text = it,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Button(
                                    onClick = { onToggleServer(server.serverId) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        when (server.status) {
                                            McpConnectionStatusUi.CONNECTED,
                                            McpConnectionStatusUi.LOADING -> "Disconnect"

                                            McpConnectionStatusUi.IDLE,
                                            McpConnectionStatusUi.ERROR -> "Connect"
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            state.error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Text("Available tools:", style = MaterialTheme.typography.titleMedium)
            if (state.tools.isEmpty()) {
                Text("No tools loaded")
            } else {
                Text(
                    text = state.tools.joinToString(separator = "\n"),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onCallGithubGetUser, modifier = Modifier.weight(1f)) {
                    Text("Call get_user")
                }
                Button(onClick = onCallGithubGetRepo, modifier = Modifier.weight(1f)) {
                    Text("Call get_repo")
                }
            }

            if (state.toolCallResult.isNotBlank()) {
                Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 1.dp) {
                    Text(
                        text = state.toolCallResult,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
