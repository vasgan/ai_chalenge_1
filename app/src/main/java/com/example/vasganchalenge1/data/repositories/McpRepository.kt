package com.example.vasganchalenge1.data.repositories

import io.ktor.client.HttpClient
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class McpRepository @Inject constructor(
    private val httpClient: HttpClient
) {
    suspend fun listTools(serverUrl: String): Result<List<String>> = runCatching {
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
        val toolsResult = client.listTools()
        toolsResult.tools.map { it.name }
    }
}
