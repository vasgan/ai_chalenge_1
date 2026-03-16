package com.example.vasganchalenge1.data.repositories

import android.util.Log
import com.example.mcpserver.GithubMcpToolRegistry
import com.example.mcpserver.LocalMcpServerManager
import com.example.mcpserver.LocalServerStatus
import com.example.mcpserver.McpToolRegistry
import com.example.mcpserver.UtilityMcpToolRegistry
import com.example.vasganchalenge1.di.GithubServer
import com.example.vasganchalenge1.di.UtilityServer
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

const val MCP_SERVER_ID_GITHUB = "github"
const val MCP_SERVER_ID_UTILITY = "utility"

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
    val requiredParams: List<String> = emptyList(),
    val serverId: String = "",
    val serverLabel: String = ""
)

data class ToolResult(
    val text: String,
    val structuredJson: String? = null,
    val isError: Boolean = false,
    val serverId: String? = null,
    val serverLabel: String? = null,
    val toolName: String? = null
)

data class RegisteredMcpServer(
    val serverId: String,
    val label: String,
    val url: String,
    val isConnected: Boolean,
    val isLocal: Boolean,
    val connectionStatus: McpConnectionStatus,
    val localServerStatus: LocalServerStatus,
    val toolsCount: Int,
    val error: String? = null
)

data class McpSharedState(
    val servers: List<RegisteredMcpServer> = emptyList(),
    val tools: List<McpTool> = emptyList(),
    val error: String? = null,
    // legacy fields for existing UI subscribers
    val serverUrl: String = "",
    val connectionStatus: McpConnectionStatus = McpConnectionStatus.DISCONNECTED,
    val localServerStatus: LocalServerStatus = LocalServerStatus.STOPPED,
    val localServerUrl: String = ""
)

private data class LocalServerEndpoint(
    val serverId: String,
    val label: String,
    val manager: LocalMcpServerManager,
    val registry: McpToolRegistry
)

private data class ServerRuntime(
    val serverId: String,
    val label: String,
    val isLocal: Boolean,
    val url: String,
    val connectionStatus: McpConnectionStatus,
    val localServerStatus: LocalServerStatus,
    val error: String?
)

@Singleton
class McpRepository @Inject constructor(
    private val httpClient: HttpClient,
    @GithubServer private val githubLocalServerManager: LocalMcpServerManager,
    @UtilityServer private val utilityLocalServerManager: LocalMcpServerManager,
    @GithubServer githubRegistry: GithubMcpToolRegistry,
    @UtilityServer utilityRegistry: UtilityMcpToolRegistry
) {
    private val tag = "McpRepository"
    private val json = Json { ignoreUnknownKeys = true }

    private val localEndpoints: Map<String, LocalServerEndpoint> = listOf(
        LocalServerEndpoint(
            serverId = MCP_SERVER_ID_GITHUB,
            label = "GitHub Local MCP",
            manager = githubLocalServerManager,
            registry = githubRegistry
        ),
        LocalServerEndpoint(
            serverId = MCP_SERVER_ID_UTILITY,
            label = "Utility Local MCP",
            manager = utilityLocalServerManager,
            registry = utilityRegistry
        )
    ).associateBy { it.serverId }

    private val runtimes: MutableMap<String, ServerRuntime> = mutableMapOf()
    private val toolsByServer: MutableMap<String, List<McpTool>> = mutableMapOf()
    private var globalError: String? = null
    private var lastSelectedServerId: String? = null

    private val _state = MutableStateFlow(McpSharedState())
    val state: StateFlow<McpSharedState> = _state.asStateFlow()

    init {
        localEndpoints.values.forEach { endpoint ->
            runtimes[endpoint.serverId] = ServerRuntime(
                serverId = endpoint.serverId,
                label = endpoint.label,
                isLocal = true,
                url = "",
                connectionStatus = McpConnectionStatus.DISCONNECTED,
                localServerStatus = LocalServerStatus.STOPPED,
                error = null
            )
        }
        emitState()
    }

    suspend fun connect(serverUrl: String): Result<List<McpTool>> {
        return connectServer(
            serverId = "remote_default",
            label = "Remote MCP",
            serverUrl = serverUrl,
            isLocal = false
        )
    }

    suspend fun connectServer(
        serverId: String,
        label: String,
        serverUrl: String,
        isLocal: Boolean
    ): Result<List<McpTool>> = runCatching {
        val normalizedId = serverId.trim().ifBlank { "remote_${System.currentTimeMillis()}" }
        val normalizedUrl = serverUrl.trim()
        require(normalizedUrl.isNotBlank()) { "Server URL is empty" }

        val previous = runtimes[normalizedId]
        val runtime = ServerRuntime(
            serverId = normalizedId,
            label = label.ifBlank { previous?.label ?: normalizedId },
            isLocal = isLocal,
            url = normalizedUrl,
            connectionStatus = McpConnectionStatus.CONNECTING,
            localServerStatus = if (isLocal) LocalServerStatus.STARTING else LocalServerStatus.STOPPED,
            error = null
        )
        runtimes[normalizedId] = runtime
        globalError = null
        emitState()

        val tools = withTimeout(MCP_TIMEOUT_MS) {
            listToolsViaRemoteMcp(
                serverId = normalizedId,
                serverLabel = runtime.label,
                serverUrl = normalizedUrl
            )
        }

        toolsByServer[normalizedId] = tools
        lastSelectedServerId = normalizedId
        runtimes[normalizedId] = runtime.copy(
            connectionStatus = McpConnectionStatus.CONNECTED,
            localServerStatus = if (isLocal) LocalServerStatus.RUNNING else LocalServerStatus.STOPPED,
            error = null
        )
        emitState()
        tools
    }.onFailure { throwable ->
        Log.e(tag, "connectServer failed. serverId=$serverId url=$serverUrl", throwable)
        globalError = throwable.message ?: "MCP connection error"
        val previous = runtimes[serverId]
        if (previous != null) {
            runtimes[serverId] = previous.copy(
                connectionStatus = McpConnectionStatus.ERROR,
                localServerStatus = if (previous.isLocal) LocalServerStatus.ERROR else previous.localServerStatus,
                error = globalError
            )
        }
        emitState()
    }

    suspend fun connectLocal(): Result<List<McpTool>> = connectLocal(MCP_SERVER_ID_GITHUB)

    suspend fun connectLocal(serverId: String): Result<List<McpTool>> = runCatching {
        val endpoint = localEndpoints[serverId]
            ?: error("Unknown local MCP server: $serverId")

        val current = runtimes[serverId] ?: ServerRuntime(
            serverId = endpoint.serverId,
            label = endpoint.label,
            isLocal = true,
            url = "",
            connectionStatus = McpConnectionStatus.DISCONNECTED,
            localServerStatus = LocalServerStatus.STOPPED,
            error = null
        )

        runtimes[serverId] = current.copy(
            connectionStatus = McpConnectionStatus.CONNECTING,
            localServerStatus = LocalServerStatus.STARTING,
            error = null
        )
        globalError = null
        emitState()

        val localUrl = withContext(Dispatchers.IO) { endpoint.manager.start() }
        val tools = listToolsViaInProcess(endpoint.registry, endpoint.serverId, endpoint.label)

        toolsByServer[serverId] = tools
        lastSelectedServerId = serverId
        runtimes[serverId] = current.copy(
            url = localUrl,
            connectionStatus = McpConnectionStatus.CONNECTED,
            localServerStatus = LocalServerStatus.RUNNING,
            error = null
        )
        emitState()
        tools
    }.onFailure { throwable ->
        Log.e(tag, "connectLocal failed. serverId=$serverId", throwable)
        globalError = throwable.message ?: "Failed to start local MCP server"
        val previous = runtimes[serverId]
        if (previous != null) {
            runtimes[serverId] = previous.copy(
                connectionStatus = McpConnectionStatus.ERROR,
                localServerStatus = LocalServerStatus.ERROR,
                error = globalError
            )
        }
        emitState()
    }

    fun disconnect() {
        disconnectAll()
    }

    fun disconnectAll() {
        runtimes.keys.toList().forEach(::disconnect)
    }

    fun disconnect(serverId: String) {
        val runtime = runtimes[serverId] ?: return
        if (runtime.isLocal) {
            runCatching { localEndpoints[serverId]?.manager?.stop() }
        }

        toolsByServer.remove(serverId)
        runtimes[serverId] = runtime.copy(
            connectionStatus = McpConnectionStatus.DISCONNECTED,
            localServerStatus = if (runtime.isLocal) LocalServerStatus.STOPPED else LocalServerStatus.STOPPED,
            error = null
        )

        if (lastSelectedServerId == serverId) {
            lastSelectedServerId = runtimes.values.firstOrNull { it.connectionStatus == McpConnectionStatus.CONNECTED }?.serverId
        }

        globalError = null
        emitState()
    }

    suspend fun listTools(): Result<List<McpTool>> {
        return listTools(lastSelectedServerId)
    }

    suspend fun listTools(serverId: String?): Result<List<McpTool>> = runCatching {
        val connectedRuntimes = runtimes.values.filter { it.connectionStatus == McpConnectionStatus.CONNECTED }
        if (connectedRuntimes.isEmpty()) error("MCP не подключён")

        if (serverId != null) {
            val runtime = runtimes[serverId] ?: error("Сервер не найден: $serverId")
            val tools = refreshTools(runtime)
            toolsByServer[serverId] = tools
            emitState()
            return@runCatching tools
        }

        val refreshed = connectedRuntimes.flatMap { runtime ->
            val tools = refreshTools(runtime)
            toolsByServer[runtime.serverId] = tools
            tools
        }
        emitState()
        refreshed
    }.onFailure { throwable ->
        Log.e(tag, "listTools failed. serverId=$serverId", throwable)
        globalError = throwable.message ?: "listTools failed"
        emitState()
    }

    suspend fun callTool(
        name: String,
        argumentsJson: String,
        preferredServerId: String? = null
    ): Result<ToolResult> {
        val resolved = resolveToolServer(name = name, preferredServerId = preferredServerId)
        if (resolved.isFailure) {
            val message = resolved.exceptionOrNull()?.message ?: "Tool routing failed"
            globalError = message
            emitState()
            return Result.success(
                ToolResult(
                    text = message,
                    structuredJson = """{"error":"$message"}""",
                    isError = true,
                    toolName = name
                )
            )
        }

        val runtime = resolved.getOrThrow()
        return try {
            val args = parseArgumentsJson(argumentsJson)
            val toolResult = withTimeout(MCP_TIMEOUT_MS) {
                val localEndpoint = localEndpoints[runtime.serverId]
                if (runtime.isLocal && localEndpoint != null) {
                    callToolViaInProcess(
                        registry = localEndpoint.registry,
                        serverId = runtime.serverId,
                        serverLabel = runtime.label,
                        toolName = name,
                        arguments = args
                    )
                } else {
                    callToolViaRemoteMcp(
                        serverId = runtime.serverId,
                        serverLabel = runtime.label,
                        serverUrl = runtime.url,
                        toolName = name,
                        arguments = args
                    )
                }
            }

            globalError = null
            emitState()
            Result.success(toolResult)
        } catch (throwable: Throwable) {
            Log.e(tag, "callTool failed. name=$name serverId=${runtime.serverId}", throwable)
            val message = throwable.message ?: "Tool call failed"
            globalError = message
            emitState()
            Result.success(
                ToolResult(
                    text = message,
                    structuredJson = """{"error":"$message"}""",
                    isError = true,
                    serverId = runtime.serverId,
                    serverLabel = runtime.label,
                    toolName = name
                )
            )
        }
    }

    private suspend fun refreshTools(runtime: ServerRuntime): List<McpTool> {
        if (runtime.connectionStatus != McpConnectionStatus.CONNECTED) {
            error("Сервер ${runtime.label} не подключён")
        }
        val localEndpoint = localEndpoints[runtime.serverId]
        return if (runtime.isLocal && localEndpoint != null) {
            listToolsViaInProcess(localEndpoint.registry, runtime.serverId, runtime.label)
        } else {
            listToolsViaRemoteMcp(runtime.serverId, runtime.label, runtime.url)
        }
    }

    private fun resolveToolServer(name: String, preferredServerId: String?): Result<ServerRuntime> {
        val connectedRuntimes = runtimes.values.filter { it.connectionStatus == McpConnectionStatus.CONNECTED }
        if (connectedRuntimes.isEmpty()) {
            return Result.failure(IllegalArgumentException("MCP не подключён"))
        }

        if (preferredServerId != null) {
            val runtime = runtimes[preferredServerId]
                ?: return Result.failure(IllegalArgumentException("Сервер не найден: $preferredServerId"))
            if (runtime.connectionStatus != McpConnectionStatus.CONNECTED) {
                return Result.failure(IllegalArgumentException("Сервер не подключён: ${runtime.label}"))
            }
            val hasTool = toolsByServer[preferredServerId].orEmpty().any { it.name == name }
            if (!hasTool) {
                return Result.failure(
                    IllegalArgumentException("Tool '$name' недоступен на сервере ${runtime.label}")
                )
            }
            return Result.success(runtime)
        }

        val candidates = connectedRuntimes.filter { runtime ->
            toolsByServer[runtime.serverId].orEmpty().any { it.name == name }
        }

        if (candidates.isEmpty()) {
            return Result.failure(IllegalArgumentException("Нет доступного tool: $name"))
        }

        if (candidates.size > 1) {
            val serverIds = candidates.joinToString { it.serverId }
            return Result.failure(
                IllegalArgumentException("Tool '$name' доступен на нескольких серверах: $serverIds. Укажи serverId.")
            )
        }

        return Result.success(candidates.first())
    }

    private suspend fun listToolsViaRemoteMcp(
        serverId: String,
        serverLabel: String,
        serverUrl: String
    ): List<McpTool> {
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
                requiredParams = extractRequiredParams(schemaJson),
                serverId = serverId,
                serverLabel = serverLabel
            )
        }
    }

    private suspend fun callToolViaRemoteMcp(
        serverId: String,
        serverLabel: String,
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
            isError = result.isError == true,
            serverId = serverId,
            serverLabel = serverLabel,
            toolName = toolName
        )
    }

    private fun listToolsViaInProcess(
        registry: McpToolRegistry,
        serverId: String,
        serverLabel: String
    ): List<McpTool> {
        return registry.listTools().mapNotNull { tool ->
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
                requiredParams = required,
                serverId = serverId,
                serverLabel = serverLabel
            )
        }
    }

    private suspend fun callToolViaInProcess(
        registry: McpToolRegistry,
        serverId: String,
        serverLabel: String,
        toolName: String,
        arguments: Map<String, Any?>
    ): ToolResult {
        val result = registry.callTool(toolName, arguments)
        val content = result["content"] as? List<*> ?: emptyList<Any?>()
        val text = content.mapNotNull { block ->
            (block as? Map<*, *>)?.get("text")?.toString()
        }.joinToString("\n")

        return ToolResult(
            text = text.ifBlank { result.toString() },
            structuredJson = result["structuredContent"]?.toJsonElement()?.toString(),
            isError = result["isError"] as? Boolean ?: false,
            serverId = serverId,
            serverLabel = serverLabel,
            toolName = toolName
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
            is JsonObject -> this.mapValues { (_, value) -> value.toAnyValue() }
            is JsonArray -> this.map { it.toAnyValue() }
            is JsonPrimitive -> {
                booleanOrNull
                    ?: longOrNull
                    ?: doubleOrNull
                    ?: content
            }
            else -> toString()
        }
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

    private fun emitState() {
        val servers = runtimes.values
            .sortedWith(compareBy<ServerRuntime> { !it.isLocal }.thenBy { it.serverId })
            .map { runtime ->
                RegisteredMcpServer(
                    serverId = runtime.serverId,
                    label = runtime.label,
                    url = runtime.url,
                    isConnected = runtime.connectionStatus == McpConnectionStatus.CONNECTED,
                    isLocal = runtime.isLocal,
                    connectionStatus = runtime.connectionStatus,
                    localServerStatus = runtime.localServerStatus,
                    toolsCount = toolsByServer[runtime.serverId].orEmpty().size,
                    error = runtime.error
                )
            }

        val tools = servers.flatMap { server ->
            toolsByServer[server.serverId].orEmpty()
        }.sortedWith(compareBy<McpTool> { it.serverLabel }.thenBy { it.name })

        val aggregateStatus = when {
            servers.any { it.connectionStatus == McpConnectionStatus.CONNECTED } -> McpConnectionStatus.CONNECTED
            servers.any { it.connectionStatus == McpConnectionStatus.CONNECTING } -> McpConnectionStatus.CONNECTING
            servers.any { it.connectionStatus == McpConnectionStatus.ERROR } -> McpConnectionStatus.ERROR
            else -> McpConnectionStatus.DISCONNECTED
        }

        val selected = servers.firstOrNull { it.serverId == lastSelectedServerId }
            ?: servers.firstOrNull { it.isConnected }
            ?: servers.firstOrNull()

        val githubServer = servers.firstOrNull { it.serverId == MCP_SERVER_ID_GITHUB }

        _state.value = McpSharedState(
            servers = servers,
            tools = tools,
            error = globalError,
            serverUrl = selected?.url.orEmpty(),
            connectionStatus = aggregateStatus,
            localServerStatus = githubServer?.localServerStatus ?: LocalServerStatus.STOPPED,
            localServerUrl = githubServer?.url.orEmpty()
        )
    }
}

private fun Map<String, Any?>.toJsonObject(): JsonObject {
    return buildJsonObject {
        entries.forEach { (key, value) ->
            put(key, value.toJsonElement())
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
