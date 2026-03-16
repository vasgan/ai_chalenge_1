package com.example.mcpserver

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

class UtilityMcpToolRegistry(
    private val summaryStorageTools: SummaryStorageTools = NoopSummaryStorageTools,
    moshi: Moshi = Moshi.Builder().build()
) : McpToolRegistry {

    private val anyMapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(
            Map::class.java,
            String::class.java,
            Any::class.java
        )
    )

    override fun listTools(): List<Map<String, Any?>> = listOf(
        mapOf(
            "name" to "summarize_github_report",
            "description" to "Сформировать краткий GitHub-отчет на основе userJson, repoJson и issuesJson.",
            "inputSchema" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "userJson" to mapOf(
                        "oneOf" to listOf(
                            mapOf("type" to "object"),
                            mapOf("type" to "string")
                        )
                    ),
                    "repoJson" to mapOf(
                        "oneOf" to listOf(
                            mapOf("type" to "object"),
                            mapOf("type" to "string")
                        )
                    ),
                    "issuesJson" to mapOf(
                        "oneOf" to listOf(
                            mapOf("type" to "array"),
                            mapOf("type" to "string")
                        )
                    )
                ),
                "required" to listOf("userJson", "repoJson", "issuesJson")
            )
        ),
        mapOf(
            "name" to "save_summary_to_file",
            "description" to "Сохранить текстовую сводку локально.",
            "inputSchema" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "title" to mapOf("type" to "string"),
                    "summaryText" to mapOf("type" to "string"),
                    "rawJson" to mapOf("type" to "string")
                ),
                "required" to listOf("title", "summaryText", "rawJson")
            )
        )
    )

    override suspend fun callTool(name: String, arguments: Map<String, Any?>): Map<String, Any?> {
        return when (name) {
            "summarize_github_report", "summarize_github_user_profile" -> {
                val userMap = normalizeJsonObject(arguments["userJson"])
                    ?: return errorResult("userJson is required")
                val repoMap = normalizeJsonObject(arguments["repoJson"])
                    ?: return errorResult("repoJson is required")
                val issues = normalizeJsonArray(arguments["issuesJson"])
                    ?: return errorResult("issuesJson is required")

                val userLogin = userMap["login"].asText()
                val userName = userMap["name"].asText()
                val publicRepos = userMap["public_repos"].asLong()
                val followers = userMap["followers"].asLong()

                val repoName = repoMap["full_name"].asText().ifBlank { repoMap["name"].asText() }
                val stars = repoMap["stargazers_count"].asLong()
                val openIssues = repoMap["open_issues_count"].asLong()
                val repoDescription = repoMap["description"].asText()

                val issuesOpen = issues.count { issue ->
                    val state = issue["state"]?.toString()?.lowercase().orEmpty()
                    state.isEmpty() || state == "open"
                }

                val summaryText = buildString {
                    append("Отчет GitHub: ")
                    append(userLogin.ifBlank { "unknown-user" })
                    if (userName.isNotBlank()) append(" ($userName)")
                    append(". Репозиторий: ")
                    append(repoName.ifBlank { "unknown-repo" })
                    append(". ")
                    append("Публичных репозиториев: ${publicRepos ?: "n/a"}, подписчиков: ${followers ?: "n/a"}. ")
                    append("Звезды репозитория: ${stars ?: "n/a"}, open issues: ${openIssues ?: issuesOpen}. ")
                    if (repoDescription.isNotBlank()) append("Описание: $repoDescription. ")
                    append("Получено issues: ${issues.size}.")
                }.trim()

                val structured = mapOf(
                    "user" to mapOf(
                        "login" to userLogin,
                        "name" to userName,
                        "publicRepos" to publicRepos,
                        "followers" to followers
                    ),
                    "repo" to mapOf(
                        "name" to repoName,
                        "stars" to stars,
                        "openIssues" to openIssues,
                        "description" to repoDescription
                    ),
                    "issuesCount" to issues.size,
                    "summaryText" to summaryText
                )

                successResult(summaryText, structured)
            }

            "save_summary_to_file" -> {
                val title = arguments["title"]?.toString().orEmpty()
                val summaryText = arguments["summaryText"]?.toString().orEmpty()
                val rawJson = arguments["rawJson"]?.toString().orEmpty()
                if (title.isBlank() || summaryText.isBlank() || rawJson.isBlank()) {
                    errorResult("title, summaryText and rawJson are required")
                } else {
                    val result = summaryStorageTools.saveSummaryToFile(
                        title = title,
                        summaryText = summaryText,
                        rawJson = rawJson
                    )
                    if (result.isError) {
                        errorResult(result.text)
                    } else {
                        successResult(result.text, result.structured)
                    }
                }
            }

            else -> errorResult("Unknown tool: $name")
        }
    }

    private fun successResult(text: String, structured: Any?): Map<String, Any?> {
        return mapOf(
            "content" to listOf(mapOf("type" to "text", "text" to text)),
            "structuredContent" to structured,
            "isError" to false
        )
    }

    private fun errorResult(message: String): Map<String, Any?> {
        return mapOf(
            "content" to listOf(mapOf("type" to "text", "text" to "Error: $message")),
            "structuredContent" to mapOf("error" to message),
            "isError" to true
        )
    }

    private fun normalizeJsonObject(value: Any?): Map<String, Any?>? {
        return when (value) {
            is Map<*, *> -> value.entries
                .filter { it.key != null }
                .associate { it.key.toString() to it.value }
            is String -> parseJsonMap(value)
            else -> null
        }
    }

    private fun normalizeJsonArray(value: Any?): List<Map<String, Any?>>? {
        return when (value) {
            is List<*> -> value.mapNotNull { entry ->
                when (entry) {
                    is Map<*, *> -> entry.entries
                        .filter { it.key != null }
                        .associate { it.key.toString() to it.value }
                    else -> null
                }
            }
            is String -> parseJsonArray(value)
            else -> null
        }
    }

    private fun parseJsonMap(raw: String): Map<String, Any?>? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        return runCatching { anyMapAdapter.fromJson(trimmed) }.getOrNull()
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseJsonArray(raw: String): List<Map<String, Any?>>? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        return runCatching {
            val root = Moshi.Builder().build().adapter(Any::class.java).fromJson(trimmed)
            (root as? List<*>)?.mapNotNull { item ->
                (item as? Map<*, *>)?.entries
                    ?.filter { it.key != null }
                    ?.associate { it.key.toString() to it.value }
            }
        }.getOrNull()
    }

    private fun Any?.asText(): String = this?.toString().orEmpty()

    private fun Any?.asLong(): Long? {
        return when (this) {
            is Number -> this.toLong()
            is String -> this.toLongOrNull()
            else -> null
        }
    }
}

