package com.example.vasganchalenge1.ui.mcp

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mcpserver.LocalServerStatus
import com.example.vasganchalenge1.data.repositories.McpConnectionStatus
import com.example.vasganchalenge1.data.repositories.McpRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject

enum class McpConnectionStatusUi {
    IDLE, LOADING, CONNECTED, ERROR
}

data class McpServerUiState(
    val serverUrl: String = "",
    val localServerStatus: LocalServerStatus = LocalServerStatus.STOPPED,
    val localServerUrl: String = "",
    val mcpConnectionStatus: McpConnectionStatusUi = McpConnectionStatusUi.IDLE,
    val tools: List<String> = emptyList(),
    val toolCallResult: String = "",
    val error: String? = null
)

@HiltViewModel
class McpServerViewModel @Inject constructor(
    private val repository: McpRepository
) : ViewModel() {
    private val tag = "McpServerViewModel"

    private val _state = MutableStateFlow(
        McpServerUiState(serverUrl = "http://10.0.2.2:8080/mcp")
    )
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.state.collect { shared ->
                _state.value = _state.value.copy(
                    serverUrl = if (shared.serverUrl.isNotBlank()) shared.serverUrl else _state.value.serverUrl,
                    localServerStatus = shared.localServerStatus,
                    localServerUrl = shared.localServerUrl,
                    mcpConnectionStatus = shared.connectionStatus.toUiStatus(),
                    tools = shared.tools.map { it.name },
                    error = shared.error
                )
            }
        }
    }

    fun setServerUrl(url: String) {
        _state.value = _state.value.copy(serverUrl = url)
    }

    fun useLocalServerAndConnect() {
        viewModelScope.launch {
            repository.connectLocal()
                .onFailure { throwable ->
                    Log.e(tag, "Failed to connect local MCP", throwable)
                }
        }
    }

    fun connectAndLoadTools(serverUrl: String = _state.value.serverUrl) {
        val normalized = serverUrl.trim()
        if (normalized.isBlank()) {
            _state.value = _state.value.copy(
                mcpConnectionStatus = McpConnectionStatusUi.ERROR,
                error = "Server URL is empty"
            )
            return
        }

        viewModelScope.launch {
            repository.connect(normalized)
                .onFailure { throwable ->
                    Log.e(tag, "MCP connect/listTools failed. url=$normalized", throwable)
                }
        }
    }

    fun callGithubGetUser(username: String = "Vasgan") {
        callTool(
            toolName = "github_get_user",
            argsJson = buildJsonObject {
                put("username", JsonPrimitive(username))
            }.toString()
        )
    }

    fun callGithubGetRepo(owner: String = "Vasgan", repo: String = "ai_chalenge_1") {
        callTool(
            toolName = "github_get_repo",
            argsJson = buildJsonObject {
                put("owner", JsonPrimitive(owner))
                put("repo", JsonPrimitive(repo))
            }.toString()
        )
    }

    private fun callTool(toolName: String, argsJson: String) {
        viewModelScope.launch {
            repository.callTool(toolName, argsJson)
                .onSuccess { output ->
                    _state.value = _state.value.copy(toolCallResult = output.text, error = null)
                }
                .onFailure { throwable ->
                    Log.e(tag, "Tool call failed. tool=$toolName args=$argsJson", throwable)
                    _state.value = _state.value.copy(
                        error = throwable.message ?: "Tool call failed"
                    )
                }
        }
    }
}

private fun McpConnectionStatus.toUiStatus(): McpConnectionStatusUi {
    return when (this) {
        McpConnectionStatus.DISCONNECTED -> McpConnectionStatusUi.IDLE
        McpConnectionStatus.CONNECTING -> McpConnectionStatusUi.LOADING
        McpConnectionStatus.CONNECTED -> McpConnectionStatusUi.CONNECTED
        McpConnectionStatus.ERROR -> McpConnectionStatusUi.ERROR
    }
}
