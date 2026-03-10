package com.example.vasganchalenge1.data.repositories

import android.util.Log
import com.example.mcpserver.GithubMcpToolRegistry
import io.ktor.client.HttpClient
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class McpRepository @Inject constructor(
    private val httpClient: HttpClient
) {
    private val tag = "McpRepository"
    private val rawJson = Json { ignoreUnknownKeys = true }
    private val rawHttpClient = OkHttpClient()
    private val localRegistry = GithubMcpToolRegistry()

    suspend fun listTools(serverUrl: String): Result<List<String>> = runCatching {
        if (isLocalServerUrl(serverUrl)) {
            withTimeout(MCP_TIMEOUT_MS) { listToolsViaInProcess() }
        } else {
            withTimeout(MCP_TIMEOUT_MS) {
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
                toolsResult.tools.map { it.name }
            }
        }
    }.recoverCatching { throwable ->
        throw toUserFriendlyThrowable("listTools", serverUrl, throwable)
    }.onFailure {
        Log.e(tag, "listTools failed. serverUrl=$serverUrl", it)
    }

    suspend fun callTool(
        serverUrl: String,
        toolName: String,
        arguments: Map<String, Any?>
    ): Result<String> = runCatching {
        if (isLocalServerUrl(serverUrl)) {
            withTimeout(MCP_TIMEOUT_MS) { callToolViaInProcess(toolName, arguments) }
        } else {
            withTimeout(MCP_TIMEOUT_MS) {
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
                result.content.joinToString("\n") { it.toString() }
            }
        }
    }.recoverCatching { throwable ->
        throw toUserFriendlyThrowable("callTool:$toolName", serverUrl, throwable)
    }.onFailure {
        Log.e(tag, "callTool failed. serverUrl=$serverUrl, tool=$toolName, args=$arguments", it)
    }

    private suspend fun listToolsViaRawJsonRpc(serverUrl: String): List<String> {
        initializeSession(serverUrl)
        val response = postJsonRpc(
            serverUrl = serverUrl,
            payload = buildJsonObject {
                put("jsonrpc", JsonPrimitive("2.0"))
                put("id", JsonPrimitive("tools-list-1"))
                put("method", JsonPrimitive("tools/list"))
                put("params", buildJsonObject {})
            }
        )

        val root = rawJson.parseToJsonElement(response).jsonObject
        val errorMessage = root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        if (!errorMessage.isNullOrBlank()) {
            error("MCP tools/list error: $errorMessage")
        }

        val tools = root["result"]
            ?.jsonObject
            ?.get("tools")
            ?.jsonArray
            ?: JsonArray(emptyList())

        return tools.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
    }

    private suspend fun callToolViaRawJsonRpc(
        serverUrl: String,
        toolName: String,
        arguments: Map<String, Any?>
    ): String {
        initializeSession(serverUrl)
        val response = postJsonRpc(
            serverUrl = serverUrl,
            payload = buildJsonObject {
                put("jsonrpc", JsonPrimitive("2.0"))
                put("id", JsonPrimitive("tool-call-1"))
                put("method", JsonPrimitive("tools/call"))
                put(
                    "params",
                    buildJsonObject {
                        put("name", JsonPrimitive(toolName))
                        put("arguments", arguments.toJsonObject())
                    }
                )
            }
        )

        val root = rawJson.parseToJsonElement(response).jsonObject
        val errorMessage = root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        if (!errorMessage.isNullOrBlank()) {
            error("MCP tools/call error: $errorMessage")
        }

        val resultObj = root["result"]?.jsonObject ?: return ""
        val content = resultObj["content"]?.jsonArray ?: JsonArray(emptyList())
        val textParts = content.mapNotNull { block ->
            block.jsonObject["text"]?.jsonPrimitive?.contentOrNull
        }
        val summary = textParts.joinToString("\n").ifBlank { resultObj.toString() }
        return summary
    }

    private fun listToolsViaInProcess(): List<String> {
        return localRegistry.listTools().mapNotNull { it["name"]?.toString() }
    }

    private suspend fun callToolViaInProcess(
        toolName: String,
        arguments: Map<String, Any?>
    ): String {
        val result = localRegistry.callTool(toolName, arguments)
        val content = result["content"] as? List<*> ?: emptyList<Any?>()
        val text = content.mapNotNull { block ->
            (block as? Map<*, *>)?.get("text")?.toString()
        }.joinToString("\n")
        return text.ifBlank { result.toString() }
    }

    private suspend fun initializeSession(serverUrl: String) {
        postJsonRpc(
            serverUrl = serverUrl,
            payload = buildJsonObject {
                put("jsonrpc", JsonPrimitive("2.0"))
                put("id", JsonPrimitive("init-1"))
                put("method", JsonPrimitive("initialize"))
                put(
                    "params",
                    buildJsonObject {
                        put("protocolVersion", JsonPrimitive("2025-03-26"))
                        put("capabilities", buildJsonObject {})
                        put(
                            "clientInfo",
                            buildJsonObject {
                                put("name", JsonPrimitive("android-assistant"))
                                put("version", JsonPrimitive("1.0.0"))
                            }
                        )
                    }
                )
            }
        )

        postJsonRpc(
            serverUrl = serverUrl,
            payload = buildJsonObject {
                put("jsonrpc", JsonPrimitive("2.0"))
                put("method", JsonPrimitive("notifications/initialized"))
                put("params", buildJsonObject {})
            }
        )
    }

    private suspend fun postJsonRpc(serverUrl: String, payload: JsonObject): String = withContext(Dispatchers.IO) {
        val body = rawJson.encodeToString(JsonObject.serializer(), payload)
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(serverUrl)
            .post(body)
            .build()

        rawHttpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                if (response.code == 405 && isLocalServerUrl(serverUrl)) {
                    error(
                        "HTTP 405 from local MCP endpoint ($serverUrl). " +
                            "Likely connected to a different local service on this port."
                    )
                }
                error("HTTP ${response.code}: $text")
            }
            text
        }
    }

    private fun isLocalServerUrl(serverUrl: String): Boolean {
        val host = runCatching { URI(serverUrl).host?.lowercase() }.getOrNull()
        return host == "127.0.0.1" || host == "localhost"
    }

    private fun toUserFriendlyThrowable(operation: String, serverUrl: String, throwable: Throwable): Throwable {
        if (throwable is TimeoutCancellationException) {
            return IllegalStateException(
                "MCP timeout while $operation. url=$serverUrl. " +
                    "Server did not respond in ${MCP_TIMEOUT_MS / 1000}s.",
                throwable
            )
        }
        return throwable
    }

    private companion object {
        const val MCP_TIMEOUT_MS = 12_000L
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
