package com.example.vasganchalenge1.data.repositories

import android.util.Log
import com.example.mcpserver.GithubMcpToolRegistry
import com.example.mcpserver.GithubTrackingTools
import com.example.mcpserver.LocalMcpServerManager
import com.example.mcpserver.LocalServerStatus
import io.ktor.client.HttpClient
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import javax.inject.Inject
import javax.inject.Singleton

private const val MCP_TIMEOUT_MS = 12_000L

enum class McpConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class McpTool(
    val name: String,
    val description: String = "",
    val inputSchemaJson: String = "",
    val requiredParams: List<String> = emptyList()
)

data class ToolResult(
    val text: String,
    val structuredJson: String? = null,
    val isError: Boolean = false
)

data class McpSharedState(
    val serverUrl: String = "",
    val connectionStatus: McpConnectionStatus = McpConnectionStatus.DISCONNECTED,
    val tools: List<McpTool> = emptyList(),
    val localServerStatus: LocalServerStatus = LocalServerStatus.STOPPED,
    val localServerUrl: String = "",
    val error: String? = null
)

@Singleton
class McpRepository @Inject constructor(
    private val httpClient: HttpClient,
    private val localServerManager: LocalMcpServerManager,
    githubTrackingTools: GithubTrackingTools
) {
    private val tag = "McpRepository"
    private val json = Json { ignoreUnknownKeys = true }
    private val localRegistry = GithubMcpToolRegistry(githubTrackingTools = githubTrackingTools)

    private val _state = MutableStateFlow(McpSharedState())
    val state: StateFlow<McpSharedState> = _state.asStateFlow()

    suspend fun connect(serverUrl: String): Result<List<McpTool>> = runCatching {
        val normalized = serverUrl.trim()
        require(normalized.isNotBlank()) { "Server URL is empty" }

        _state.value = _state.value.copy(
            serverUrl = normalized,
            connectionStatus = McpConnectionStatus.CONNECTING,
            error = null
        )

        val tools = withTimeout(MCP_TIMEOUT_MS) {
            if (isLocalServerUrl(normalized)) {
                listToolsViaInProcess()
            } else {
                listToolsViaRemoteMcp(normalized)
            }
        }

        _state.value = _state.value.copy(
            serverUrl = normalized,
            connectionStatus = McpConnectionStatus.CONNECTED,
            tools = tools,
            error = null
        )

        tools
    }.onFailure { throwable ->
        Log.e(tag, "connect failed. url=$serverUrl", throwable)
        _state.value = _state.value.copy(
            connectionStatus = McpConnectionStatus.ERROR,
            error = throwable.message ?: "MCP connection error"
        )
    }

    suspend fun connectLocal(): Result<List<McpTool>> = runCatching {
        _state.value = _state.value.copy(
            localServerStatus = LocalServerStatus.STARTING,
            connectionStatus = McpConnectionStatus.CONNECTING,
            error = null
        )

        val localUrl = withContext(Dispatchers.IO) { localServerManager.start() }
        _state.value = _state.value.copy(
            localServerStatus = LocalServerStatus.RUNNING,
            localServerUrl = localUrl,
            serverUrl = localUrl
        )

        connect(localUrl).getOrThrow()
    }.onFailure { throwable ->
        Log.e(tag, "connectLocal failed", throwable)
        _state.value = _state.value.copy(
            localServerStatus = LocalServerStatus.ERROR,
            connectionStatus = McpConnectionStatus.ERROR,
            error = throwable.message ?: "Failed to start local MCP server"
        )
    }

    fun disconnect() {
        runCatching { localServerManager.stop() }
        _state.value = _state.value.copy(
            connectionStatus = McpConnectionStatus.DISCONNECTED,
            localServerStatus = LocalServerStatus.STOPPED,
            tools = emptyList(),
            error = null
        )
    }

    suspend fun listTools(): Result<List<McpTool>> = runCatching {
        val current = _state.value
        require(current.connectionStatus == McpConnectionStatus.CONNECTED) { "MCP не подключён" }

        val tools = withTimeout(MCP_TIMEOUT_MS) {
            if (isLocalServerUrl(current.serverUrl)) {
                listToolsViaInProcess()
            } else {
                listToolsViaRemoteMcp(current.serverUrl)
            }
        }

        _state.value = _state.value.copy(tools = tools, error = null)
        tools
    }.onFailure { throwable ->
        Log.e(tag, "listTools failed", throwable)
        _state.value = _state.value.copy(error = throwable.message ?: "listTools failed")
    }

    suspend fun callTool(name: String, argumentsJson: String): Result<ToolResult> {
        val current = _state.value
        if (current.connectionStatus != McpConnectionStatus.CONNECTED) {
            val message = "MCP не подключён"
            _state.value = _state.value.copy(error = message)
            return Result.success(
                ToolResult(
                    text = message,
                    structuredJson = """{"error":"$message"}""",
                    isError = true
                )
            )
        }
        if (current.tools.isEmpty()) {
            val message = "Нет доступных MCP tools"
            _state.value = _state.value.copy(error = message)
            return Result.success(
                ToolResult(
                    text = message,
                    structuredJson = """{"error":"$message"}""",
                    isError = true
                )
            )
        }

        return try {
            val args = parseArgumentsJson(argumentsJson)
            val toolResult = withTimeout(MCP_TIMEOUT_MS) {
                if (isLocalServerUrl(current.serverUrl)) {
                    callToolViaInProcess(name, args)
                } else {
                    callToolViaRemoteMcp(current.serverUrl, name, args)
                }
            }
            Result.success(toolResult)
        } catch (throwable: Throwable) {
            Log.e(tag, "callTool failed. name=$name", throwable)
            val message = throwable.message ?: "Tool call failed"
            _state.value = _state.value.copy(error = message)
            Result.success(
                ToolResult(
                    text = message,
                    structuredJson = """{"error":"$message"}""",
                    isError = true
                )
            )
        }
    }

    private suspend fun listToolsViaRemoteMcp(serverUrl: String): List<McpTool> {
        val client = Client(
            clientInfo = Implementation(
                name = "android-assistant",
                version = "1.0.0"
            )
        )
        val transport = StreamableHttpClientTransport(
            client = httpClient,
            url = serverUrl
        )

        client.connect(transport)
        val toolsResult = client.listTools(ListToolsRequest())
        return toolsResult.tools.map { tool ->
            val schemaJson = tool.inputSchema?.toString().orEmpty()
            McpTool(
                name = tool.name,
                description = tool.description ?: "",
                inputSchemaJson = schemaJson,
                requiredParams = extractRequiredParams(schemaJson)
            )
        }
    }

    private suspend fun callToolViaRemoteMcp(
        serverUrl: String,
        toolName: String,
        arguments: Map<String, Any?>
    ): ToolResult {
        val client = Client(
            clientInfo = Implementation(
                name = "android-assistant",
                version = "1.0.0"
            )
        )
        val transport = StreamableHttpClientTransport(
            client = httpClient,
            url = serverUrl
        )

        client.connect(transport)
        val result = client.callTool(
            CallToolRequest(
                params = CallToolRequestParams(
                    name = toolName,
                    arguments = arguments.toJsonObject()
                )
            )
        )

        val text = result.content.joinToString("\n") { it.toString() }
        return ToolResult(
            text = text,
            structuredJson = result.structuredContent?.toString(),
            isError = result.isError == true
        )
    }

    private fun listToolsViaInProcess(): List<McpTool> {
        return localRegistry.listTools().mapNotNull { tool ->
            val name = tool["name"]?.toString().orEmpty().ifBlank { return@mapNotNull null }
            val description = tool["description"]?.toString().orEmpty()
            val schemaAny = tool["inputSchema"]
            val schemaJson = schemaAny?.toJsonElement()?.toString().orEmpty()
            val required = (schemaAny as? Map<*, *>)?.get("required")
                .let { it as? List<*> }
                .orEmpty()
                .mapNotNull { it?.toString() }

            McpTool(
                name = name,
                description = description,
                inputSchemaJson = schemaJson,
                requiredParams = required
            )
        }
    }

    private suspend fun callToolViaInProcess(
        toolName: String,
        arguments: Map<String, Any?>
    ): ToolResult {
        val result = localRegistry.callTool(toolName, arguments)
        val content = result["content"] as? List<*> ?: emptyList<Any?>()
        val text = content.mapNotNull { block ->
            (block as? Map<*, *>)?.get("text")?.toString()
        }.joinToString("\n")

        return ToolResult(
            text = text.ifBlank { result.toString() },
            structuredJson = (result["structuredContent"] as? Map<*, *>)?.toString(),
            isError = result["isError"] as? Boolean ?: false
        )
    }

    private fun parseArgumentsJson(argumentsJson: String): Map<String, Any?> {
        val trimmed = argumentsJson.trim().ifBlank { "{}" }
        val root = json.parseToJsonElement(trimmed)
        require(root is JsonObject) { "argumentsJson должен быть JSON объектом" }
        return root.mapValues { (_, value) -> value.toAnyValue() }
    }

    private fun JsonElement.toAnyValue(): Any? {
        return when (this) {
            is JsonObject -> this.toString()
            is JsonArray -> this.toString()
            is JsonPrimitive -> {
                booleanOrNull
                    ?: longOrNull
                    ?: doubleOrNull
                    ?: content
            }
            else -> toString()
        }
    }

    private fun isLocalServerUrl(serverUrl: String): Boolean {
        val host = runCatching { java.net.URI(serverUrl).host?.lowercase() }.getOrNull()
        return host == "127.0.0.1" || host == "localhost"
    }

    private fun extractRequiredParams(schemaJson: String): List<String> {
        if (schemaJson.isBlank()) return emptyList()
        val schema = runCatching { json.parseToJsonElement(schemaJson) as? JsonObject }.getOrNull()
            ?: return emptyList()
        val required = schema["required"] as? JsonArray ?: return emptyList()
        return required.mapNotNull { element ->
            runCatching { element.jsonPrimitive.content }.getOrNull()
        }
    }
}

private fun Map<String, Any?>.toJsonObject(): JsonObject {
    return buildJsonObject {
        entries.forEach { (key, value) ->
            when (value) {
                null -> put(key, JsonPrimitive(""))
                is Boolean -> put(key, JsonPrimitive(value))
                is Number -> put(key, JsonPrimitive(value))
                else -> put(key, JsonPrimitive(value.toString()))
            }
        }
    }
}

private fun Any?.toJsonElement(): JsonElement {
    return when (this) {
        null -> JsonNull
        is JsonElement -> this
        is Map<*, *> -> {
            buildJsonObject {
                this@toJsonElement.entries.forEach { (key, value) ->
                    if (key != null) put(key.toString(), value.toJsonElement())
                }
            }
        }
        is List<*> -> {
            buildJsonArray {
                this@toJsonElement.forEach { add(it.toJsonElement()) }
            }
        }
        is Number -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        else -> JsonPrimitive(toString())
    }
}
