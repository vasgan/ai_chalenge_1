package com.example.vasganchalenge1.ui.mcp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private const val DEFAULT_MCP_SERVER_URL = "http://10.0.2.2:8080/mcp"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpServerScreen(
    state: McpServerUiState,
    onBack: () -> Unit,
    onConnect: (String) -> Unit
) {
    var serverUrl by remember { mutableStateOf(DEFAULT_MCP_SERVER_URL) }

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
                value = serverUrl,
                onValueChange = { serverUrl = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Server URL") }
            )

            Button(
                onClick = { onConnect(serverUrl) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Connect MCP")
            }

            when (state) {
                McpServerUiState.Idle -> {
                    Text("Подключение не выполнено", style = MaterialTheme.typography.bodyMedium)
                }

                McpServerUiState.Loading -> {
                    CircularProgressIndicator()
                }

                is McpServerUiState.Error -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                is McpServerUiState.Success -> {
                    Text("Доступные tools:", style = MaterialTheme.typography.titleMedium)
                    if (state.tools.isEmpty()) {
                        Text("Сервер вернул пустой список")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                }
            }
        }
    }
}
