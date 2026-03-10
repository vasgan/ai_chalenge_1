package com.example.mcpserver

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders

class GithubApiClient(
    private val httpClient: HttpClient = HttpClient(),
    moshi: Moshi = Moshi.Builder().build()
) {
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(
            Map::class.java,
            String::class.java,
            Any::class.java
        )
    )
    private val listAdapter = moshi.adapter<List<Map<String, Any?>>>(
        Types.newParameterizedType(
            List::class.java,
            Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        )
    )

    suspend fun getUser(username: String): Result<Map<String, Any?>> = runCatching {
        val json = httpClient.get("https://api.github.com/users/$username") {
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header(HttpHeaders.UserAgent, "android-local-mcp")
        }.body<String>()
        mapAdapter.fromJson(json) ?: error("Failed to parse GitHub user response")
    }

    suspend fun getRepo(owner: String, repo: String): Result<Map<String, Any?>> = runCatching {
        val json = httpClient.get("https://api.github.com/repos/$owner/$repo") {
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header(HttpHeaders.UserAgent, "android-local-mcp")
        }.body<String>()
        mapAdapter.fromJson(json) ?: error("Failed to parse GitHub repo response")
    }

    suspend fun listRepoIssues(owner: String, repo: String): Result<List<Map<String, Any?>>> = runCatching {
        val json = httpClient.get("https://api.github.com/repos/$owner/$repo/issues?state=open&per_page=10") {
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header(HttpHeaders.UserAgent, "android-local-mcp")
        }.body<String>()
        listAdapter.fromJson(json) ?: error("Failed to parse GitHub issues response")
    }
}
