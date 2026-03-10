package com.example.mcpserver

import com.squareup.moshi.JsonReader
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.readRemaining
import okio.Buffer
import kotlinx.io.readByteArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class EmbeddedMcpServer(
    private val host: String = "127.0.0.1",
    private val preferredPort: Int = 8787,
    private val toolRegistry: GithubMcpToolRegistry = GithubMcpToolRegistry(),
    moshi: Moshi = Moshi.Builder().build()
) {
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(
            Map::class.java,
            String::class.java,
            Any::class.java
        )
    )
    private val anyAdapter = moshi.adapter(Any::class.java)

    private var engine: EmbeddedServer<*, *>? = null
    @Volatile
    private var actualPort: Int = preferredPort

    fun start() {
        if (engine != null) {
            if (waitUntilEndpointReady(actualPort)) return
            runCatching { engine?.stop() }
            engine = null
        }

        val candidatePorts = buildList {
            repeat(6) { add(findEphemeralPort()) }
            add(preferredPort)
        }.filter { it > 0 }.distinct()

        for (candidatePort in candidatePorts) {
            val candidate = createServer(candidatePort)
            val started = runCatching {
                candidate.start(wait = false)
            }.onFailure {
                logError("Failed to bind/start local MCP on $host:$candidatePort", it)
                runCatching { candidate.stop() }
            }.isSuccess

            if (started) {
                if (waitUntilEndpointReady(candidatePort)) {
                    actualPort = candidatePort
                    engine = candidate
                    logInfo("Local MCP server is running on $host:$actualPort")
                    return
                }
                logError("Started on $host:$candidatePort but MCP endpoint is not ready, trying next port")
                runCatching { candidate.stop() }
            }
        }

        throw IllegalStateException("Failed to start local MCP server on any available port")
    }

    fun stop() {
        engine?.stop()
        engine = null
    }

    fun url(): String = "http://$host:$actualPort/mcp"

    private fun successResponse(id: Any?, result: Map<String, Any?>): String {
        return mapAdapter.toJson(
            linkedMapOf(
                "jsonrpc" to "2.0",
                "id" to id,
                "result" to result
            )
        )
    }

    private fun errorResponse(id: Any?, code: Int, message: String): String {
        return mapAdapter.toJson(
            linkedMapOf(
                "jsonrpc" to "2.0",
                "id" to id,
                "error" to mapOf(
                    "code" to code,
                    "message" to message
                )
            )
        )
    }

    private fun parseJsonRpcPayload(payloadText: String): Map<String, Any?>? {
        var trimmed = payloadText.trim()
        if (trimmed.isEmpty()) return null

        // Support urlencoded wrappers like "payload={...}" or "message={...}"
        if (trimmed.startsWith("payload=") || trimmed.startsWith("message=")) {
            val idx = trimmed.indexOf('=')
            if (idx >= 0 && idx + 1 < trimmed.length) {
                val encoded = trimmed.substring(idx + 1)
                trimmed = runCatching {
                    URLDecoder.decode(encoded, StandardCharsets.UTF_8.name())
                }.getOrDefault(encoded)
            }
        }

        // 1) Fast path: parse first JSON value in lenient mode.
        parseFirstJsonValue(trimmed)?.let { value ->
            when (value) {
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    return value as? Map<String, Any?>
                }
                is List<*> -> {
                    val first = value.firstOrNull() as? Map<*, *> ?: return null
                    @Suppress("UNCHECKED_CAST")
                    return first as? Map<String, Any?>
                }
                is String -> {
                    val nested = value.trim()
                    if (nested.startsWith("{") || nested.startsWith("[")) {
                        return parseJsonRpcPayload(nested)
                    }
                }
            }
        }

        // 2) "data: {json}" style payload
        if (trimmed.contains("data:")) {
            val dataPayload = trimmed.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.startsWith("data:") }
                ?.removePrefix("data:")
                ?.trim()
            if (!dataPayload.isNullOrBlank()) {
                parseFirstJsonValue(dataPayload)?.let { value ->
                    if (value is Map<*, *>) {
                        @Suppress("UNCHECKED_CAST")
                        return value as? Map<String, Any?>
                    }
                }
            }
        }

        // 3) NDJSON fallback: first valid line
        trimmed.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("{") && it.endsWith("}") }
            .forEach { line ->
                runCatching { mapAdapter.fromJson(line) }.getOrNull()?.let { return it }
            }

        // 4) Fallback: extract first balanced JSON object from text.
        extractFirstBalancedJsonObject(trimmed)?.let { extracted ->
            return runCatching { mapAdapter.fromJson(extracted) }.getOrNull()
        }

        val parsed = runCatching { anyAdapter.fromJson(trimmed) }.getOrNull()
        when (parsed) {
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                return parsed as? Map<String, Any?>
            }
            is List<*> -> {
                val first = parsed.firstOrNull() as? Map<*, *> ?: return null
                @Suppress("UNCHECKED_CAST")
                return first as? Map<String, Any?>
            }
        }

        val extracted = extractJsonObject(trimmed) ?: return null
        return runCatching { mapAdapter.fromJson(extracted) }.getOrNull()
    }

    private fun parseFirstJsonValue(raw: String): Any? {
        return runCatching {
            val reader = JsonReader.of(Buffer().writeUtf8(raw))
            reader.isLenient = true
            reader.readJsonValue()
        }.getOrNull()
    }

    private fun extractJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return raw.substring(start, end + 1)
    }

    private fun extractFirstBalancedJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        if (start < 0) return null
        var depth = 0
        for (index in start until raw.length) {
            when (raw[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return raw.substring(start, index + 1)
                    }
                }
            }
        }
        return null
    }

    private fun logInfo(message: String) {
        System.out.println("[LocalMcpServer][INFO] $message")
    }

    private fun logError(message: String, throwable: Throwable? = null) {
        System.err.println("[LocalMcpServer][ERROR] $message")
        throwable?.printStackTrace()
    }

    private fun isPortAvailable(port: Int): Boolean {
        return try {
            ServerSocket(port).use { true }
        } catch (_: IOException) {
            false
        }
    }

    private fun findEphemeralPort(): Int {
        return try {
            ServerSocket(0).use { socket -> socket.localPort }
        } catch (e: IOException) {
            logError("Failed to allocate ephemeral port, fallback to preferredPort=$preferredPort", e)
            preferredPort
        }
    }

    private fun createServer(port: Int): EmbeddedServer<*, *> {
        return embeddedServer(CIO, host = host, port = port) {
            routing {
                get("/mcp") {
                    // Helps diagnose transport probing and keeps endpoint from 404 on GET.
                    call.respondText(
                        text = "{\"status\":\"ok\",\"name\":\"local-github-mcp\"}",
                        status = HttpStatusCode.OK,
                        contentType = ContentType.Application.Json
                    )
                }

                post("/mcp") {
                    val payloadBytes = call.receiveChannel().readRemaining().readByteArray()
                    val payloadText = payloadBytes.toString(Charsets.UTF_8)
                    val contentType = call.request.headers["Content-Type"]
                    val contentLength = call.request.headers["Content-Length"]
                    val transferEncoding = call.request.headers["Transfer-Encoding"]
                    val expect = call.request.headers["Expect"]
                    val headersDump = call.request.headers.entries()
                        .joinToString { entry -> "${entry.key}=${entry.value}" }
                    logInfo(
                        "POST /mcp contentType=$contentType " +
                            "contentLength=$contentLength " +
                            "transferEncoding=$transferEncoding " +
                            "expect=$expect " +
                            "bytesRead=${payloadBytes.size} " +
                            "headers=$headersDump"
                    )
                    val payload = parseJsonRpcPayload(payloadText)
                        ?: run {
                            logError(
                                "Invalid JSON payload received by local MCP server. payload=${payloadText.take(1000)}"
                            )
                            return@post call.respondText(
                                text = errorResponse(
                                    id = null,
                                    code = -32700,
                                    message = "Invalid JSON payload for MCP request"
                                ),
                                status = HttpStatusCode.OK,
                                contentType = ContentType.Application.Json
                            )
                        }

                    val method = payload["method"] as? String
                    val id = payload["id"]
                    val params = payload["params"] as? Map<*, *>

                    logInfo("Incoming MCP method=$method id=$id")

                    val response = when (method) {
                        "initialize" -> successResponse(
                            id = id,
                            result = mapOf(
                                "protocolVersion" to "2025-03-26",
                                "capabilities" to mapOf(
                                    "tools" to mapOf("listChanged" to false)
                                ),
                                "serverInfo" to mapOf(
                                    "name" to "local-github-mcp",
                                    "version" to "1.0.0"
                                ),
                                "instructions" to "Local MCP server with GitHub tools."
                            )
                        )

                        "notifications/initialized" -> ""
                        "tools/list" -> successResponse(
                            id = id,
                            result = mapOf("tools" to toolRegistry.listTools())
                        )

                        "tools/call" -> {
                            val toolName = params?.get("name")?.toString().orEmpty()
                            val args = (params?.get("arguments") as? Map<*, *>)?.mapNotNull { entry ->
                                val key = entry.key as? String ?: return@mapNotNull null
                                key to entry.value
                            }?.toMap().orEmpty()
                            logInfo("Calling tool=$toolName args=$args")
                            val result = runCatching {
                                toolRegistry.callTool(toolName, args)
                            }.onFailure {
                                logError("tools/call failed for tool=$toolName args=$args", it)
                            }.getOrElse {
                                mapOf(
                                    "content" to listOf(
                                        mapOf(
                                            "type" to "text",
                                            "text" to "Error: ${it.message ?: "tool execution failed"}"
                                        )
                                    ),
                                    "structuredContent" to mapOf(
                                        "error" to (it.message ?: "tool execution failed")
                                    ),
                                    "isError" to true
                                )
                            }
                            successResponse(id = id, result = result)
                        }

                        else -> {
                            logError("Unknown MCP method: $method payload=${payloadText.take(1000)}")
                            errorResponse(
                                id = id,
                                code = -32601,
                                message = "Method not found: $method"
                            )
                        }
                    }

                    if (method == "notifications/initialized") {
                        call.respondText(
                            text = "",
                            status = HttpStatusCode.OK,
                            contentType = ContentType.Application.Json
                        )
                    } else {
                        call.respondText(
                            text = response,
                            status = HttpStatusCode.OK,
                            contentType = ContentType.Application.Json
                        )
                    }
                }
            }
        }
    }

    private fun waitUntilEndpointReady(port: Int, timeoutMs: Long = 2_500L): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isOurEndpointReady(port)) return true
            Thread.sleep(100)
        }
        return false
    }

    private fun isOurEndpointReady(port: Int): Boolean {
        return runCatching {
            val connection = (URL("http://$host:$port/mcp").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 300
                readTimeout = 300
            }
            val responseCode = connection.responseCode
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            responseCode == 200 && body.contains("\"name\":\"local-github-mcp\"")
        }.getOrDefault(false)
    }
}
