package com.example.vasganchalenge1.ui.mcp

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mcpserver.LocalMcpServerManager
import com.example.mcpserver.LocalServerStatus
import com.example.vasganchalenge1.data.repositories.McpRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class McpConnectionStatus {
    IDLE, LOADING, CONNECTED, ERROR
}

data class McpServerUiState(
    val serverUrl: String = "",
    val localServerStatus: LocalServerStatus = LocalServerStatus.STOPPED,
    val localServerUrl: String = "",
    val mcpConnectionStatus: McpConnectionStatus = McpConnectionStatus.IDLE,
    val tools: List<String> = emptyList(),
    val toolCallResult: String = "",
    val error: String? = null
)

@HiltViewModel
class McpServerViewModel @Inject constructor(
    private val repository: McpRepository,
    private val localServerManager: LocalMcpServerManager
) : ViewModel() {
    private val tag = "McpServerViewModel"
    private val _state = MutableStateFlow(
        McpServerUiState(
            serverUrl = "http://10.0.2.2:8080/mcp"
        )
    )
    val state = _state.asStateFlow()

    fun setServerUrl(url: String) {
        _state.value = _state.value.copy(serverUrl = url)
    }

    fun useLocalServerAndConnect() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                localServerStatus = LocalServerStatus.STARTING,
                mcpConnectionStatus = McpConnectionStatus.LOADING,
                error = null
            )

            val localUrl = runCatching {
                withContext(Dispatchers.IO) { localServerManager.start() }
            }
                .onFailure {
                    Log.e(tag, "Failed to start local MCP server", it)
                    _state.value = _state.value.copy(
                        localServerStatus = LocalServerStatus.ERROR,
                        mcpConnectionStatus = McpConnectionStatus.ERROR,
                        error = it.message ?: "Failed to start local MCP server"
                    )
                }
                .getOrNull() ?: return@launch

            _state.value = _state.value.copy(
                localServerStatus = LocalServerStatus.RUNNING,
                localServerUrl = localUrl,
                serverUrl = localUrl
            )
            connectAndLoadTools(localUrl)
        }
    }

    fun connectAndLoadTools(serverUrl: String = _state.value.serverUrl) {
        val normalized = serverUrl.trim()
        if (normalized.isBlank()) {
            _state.value = _state.value.copy(
                mcpConnectionStatus = McpConnectionStatus.ERROR,
                error = "Server URL is empty"
            )
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(
                mcpConnectionStatus = McpConnectionStatus.LOADING,
                error = null,
                serverUrl = normalized
            )

            val effectiveUrl = if (isLocalHostUrl(normalized)) {
                _state.value = _state.value.copy(localServerStatus = LocalServerStatus.STARTING)
                runCatching {
                    withContext(Dispatchers.IO) { localServerManager.start() }
                }
                    .onSuccess { localUrl ->
                        _state.value = _state.value.copy(
                            localServerStatus = LocalServerStatus.RUNNING,
                            localServerUrl = localUrl,
                            serverUrl = localUrl
                        )
                    }
                    .onFailure { throwable ->
                        Log.e(tag, "Failed to auto-start local MCP server for url=$normalized", throwable)
                        _state.value = _state.value.copy(
                            localServerStatus = LocalServerStatus.ERROR,
                            mcpConnectionStatus = McpConnectionStatus.ERROR,
                            error = throwable.message ?: "Failed to start local MCP server"
                        )
                    }
                    .getOrNull() ?: return@launch
            } else {
                normalized
            }

            repository.listTools(effectiveUrl)
                .onSuccess { tools ->
                    _state.value = _state.value.copy(
                        mcpConnectionStatus = McpConnectionStatus.CONNECTED,
                        tools = tools
                    )
                }
                .onFailure { throwable ->
                    Log.e(tag, "MCP connect/listTools failed. url=$normalized", throwable)
                    _state.value = _state.value.copy(
                        mcpConnectionStatus = McpConnectionStatus.ERROR,
                        error = throwable.message ?: "MCP connection error"
                    )
                }
        }
    }

    private fun isLocalHostUrl(url: String): Boolean {
        val host = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull()
        return host == "127.0.0.1" || host == "localhost"
    }

    fun callGithubGetUser(username: String = "Vasgan") {
        callTool(
            toolName = "github_get_user",
            args = mapOf("username" to username)
        )
    }

    fun callGithubGetRepo(owner: String = "Vasgan", repo: String = "ai_chalenge_1") {
        callTool(
            toolName = "github_get_repo",
            args = mapOf("owner" to owner, "repo" to repo)
        )
    }

    private fun callTool(toolName: String, args: Map<String, Any?>) {
        val url = _state.value.serverUrl
        if (url.isBlank()) {
            _state.value = _state.value.copy(error = "Connect to MCP first")
            return
        }
        viewModelScope.launch {
            repository.callTool(url, toolName, args)
                .onSuccess { output ->
                    _state.value = _state.value.copy(toolCallResult = output, error = null)
                }
                .onFailure { throwable ->
                    Log.e(tag, "Tool call failed. tool=$toolName args=$args", throwable)
                    _state.value = _state.value.copy(
                        error = throwable.message ?: "Tool call failed"
                    )
                }
        }
    }
}
