package com.example.vasganchalenge1.ui.mcp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
    onConnect: () -> Unit,
    onUseLocalMcp: () -> Unit,
    onCallGithubGetUser: () -> Unit,
    onCallGithubGetRepo: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MCP - Server") },
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
                label = { Text("Server URL") }
            )

            Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
                Text("Connect MCP")
            }
            Button(onClick = onUseLocalMcp, modifier = Modifier.fillMaxWidth()) {
                Text("Использовать локальный MCP")
            }

            Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 1.dp) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Local server status: ${state.localServerStatus}")
                    Text("Local server URL: ${state.localServerUrl.ifBlank { "—" }}")
                    Text("MCP connection status: ${state.mcpConnectionStatus}")
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
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.tools, key = { it }) { tool ->
                        Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 1.dp) {
                            Text(
                                text = tool,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            Button(onClick = onCallGithubGetUser, modifier = Modifier.fillMaxWidth()) {
                Text("Call github_get_user(Vasgan)")
            }
            Button(onClick = onCallGithubGetRepo, modifier = Modifier.fillMaxWidth()) {
                Text("Call github_get_repo(Vasgan, ai_chalenge_1)")
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
