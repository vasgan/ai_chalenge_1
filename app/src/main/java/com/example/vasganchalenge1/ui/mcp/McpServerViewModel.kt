package com.example.vasganchalenge1.ui.mcp

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vasganchalenge1.data.repositories.MCP_SERVER_ID_GITHUB
import com.example.vasganchalenge1.data.repositories.MCP_SERVER_ID_UTILITY
import com.example.vasganchalenge1.data.repositories.McpConnectionStatus
import com.example.vasganchalenge1.data.repositories.McpRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject

enum class McpConnectionStatusUi {
    IDLE, LOADING, CONNECTED, ERROR
}

data class McpServerItemUi(
    val serverId: String,
    val label: String,
    val url: String,
    val isLocal: Boolean,
    val status: McpConnectionStatusUi,
    val toolsCount: Int,
    val error: String? = null
)

data class McpServerUiState(
    val serverUrl: String = "",
    val servers: List<McpServerItemUi> = emptyList(),
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
                    serverUrl = _state.value.serverUrl,
                    servers = shared.servers.map { server ->
                        McpServerItemUi(
                            serverId = server.serverId,
                            label = server.label,
                            url = server.url,
                            isLocal = server.isLocal,
                            status = server.connectionStatus.toUiStatus(),
                            toolsCount = server.toolsCount,
                            error = server.error
                        )
                    },
                    tools = shared.tools.map { tool -> "[${tool.serverId}] ${tool.name}" },
                    error = shared.error
                )
            }
        }
    }

    fun setServerUrl(url: String) {
        _state.value = _state.value.copy(serverUrl = url)
    }

    fun connectGithubLocal() {
        connectLocal(MCP_SERVER_ID_GITHUB)
    }

    fun connectUtilityLocal() {
        connectLocal(MCP_SERVER_ID_UTILITY)
    }

    fun connectAndLoadTools(serverUrl: String = _state.value.serverUrl) {
        val normalized = serverUrl.trim()
        if (normalized.isBlank()) {
            _state.value = _state.value.copy(error = "Server URL is empty")
            return
        }

        viewModelScope.launch {
            val remoteId = buildRemoteServerId(normalized)
            repository.connectServer(
                serverId = remoteId,
                label = "Remote MCP",
                serverUrl = normalized,
                isLocal = false
            ).onFailure { throwable ->
                Log.e(tag, "MCP connect/listTools failed. url=$normalized", throwable)
            }
        }
    }

    fun toggleServer(serverId: String) {
        val server = _state.value.servers.firstOrNull { it.serverId == serverId } ?: return
        when (server.status) {
            McpConnectionStatusUi.CONNECTED,
            McpConnectionStatusUi.LOADING -> repository.disconnect(serverId)

            McpConnectionStatusUi.IDLE,
            McpConnectionStatusUi.ERROR -> {
                if (server.isLocal) {
                    connectLocal(serverId)
                } else {
                    connectAndLoadTools(server.url)
                }
            }
        }
    }

    fun callGithubGetUser(username: String = "Vasgan") {
        callTool(
            serverId = MCP_SERVER_ID_GITHUB,
            toolName = "github_get_user",
            argsJson = buildJsonObject {
                put("username", JsonPrimitive(username))
            }.toString()
        )
    }

    fun callGithubGetRepo(owner: String = "Vasgan", repo: String = "ai_chalenge_1") {
        callTool(
            serverId = MCP_SERVER_ID_GITHUB,
            toolName = "github_get_repo",
            argsJson = buildJsonObject {
                put("owner", JsonPrimitive(owner))
                put("repo", JsonPrimitive(repo))
            }.toString()
        )
    }

    private fun connectLocal(serverId: String) {
        viewModelScope.launch {
            repository.connectLocal(serverId)
                .onFailure { throwable ->
                    Log.e(tag, "Failed to connect local MCP serverId=$serverId", throwable)
                }
        }
    }

    private fun callTool(serverId: String, toolName: String, argsJson: String) {
        viewModelScope.launch {
            repository.callTool(
                name = toolName,
                argumentsJson = argsJson,
                preferredServerId = serverId
            ).onSuccess { output ->
                _state.value = _state.value.copy(toolCallResult = output.text, error = null)
            }.onFailure { throwable ->
                Log.e(tag, "Tool call failed. tool=$toolName args=$argsJson", throwable)
                _state.value = _state.value.copy(
                    error = throwable.message ?: "Tool call failed"
                )
            }
        }
    }

    private fun buildRemoteServerId(url: String): String {
        return "remote_${url.hashCode().toUInt().toString(16)}"
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
