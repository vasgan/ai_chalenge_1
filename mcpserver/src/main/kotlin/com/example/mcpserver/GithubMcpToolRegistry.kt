package com.example.mcpserver

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlin.math.roundToInt

class GithubMcpToolRegistry(
    private val githubApiClient: GithubApiClient = GithubApiClient(),
    private val githubTrackingTools: GithubTrackingTools = NoopGithubTrackingTools,
    private val summaryStorageTools: SummaryStorageTools = NoopSummaryStorageTools
) {
    private val anyMapAdapter = Moshi.Builder().build().adapter<Map<String, Any?>>(
        Types.newParameterizedType(
            Map::class.java,
            String::class.java,
            Any::class.java
        )
    )

    fun listTools(): List<Map<String, Any?>> = listOf(
        mapOf(
            "name" to "github_get_user",
            "description" to "Получить профиль пользователя GitHub по username. Используй для запросов: кто такой пользователь, сколько у него репозиториев, сколько подписчиков. Возвращает поля профиля, включая public_repos.",
            "inputSchema" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "username" to mapOf("type" to "string")
                ),
                "required" to listOf("username")
            )
        ),
        mapOf(
            "name" to "github_get_repo",
            "description" to "Получить информацию о репозитории GitHub по owner и repo. Используй для запросов о конкретном репозитории: звезды, форки, open issues, описание.",
            "inputSchema" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "owner" to mapOf("type" to "string"),
                    "repo" to mapOf("type" to "string")
                ),
                "required" to listOf("owner", "repo")
            )
        ),
        mapOf(
            "name" to "github_list_repo_issues",
            "description" to "Получить список открытых issues репозитория по owner и repo. Используй для запросов: покажи issues, какие открытые проблемы в репозитории.",
            "inputSchema" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "owner" to mapOf("type" to "string"),
                    "repo" to mapOf("type" to "string")
                ),
                "required" to listOf("owner", "repo")
            )
        ),
        mapOf(
            "name" to "github_schedule_user_stars_tracking",
            "description" to "Запустить единственный активный периодический сбор метрики пользователя GitHub (по умолчанию total_stars) через self-rescheduling OneTimeWorkRequest. Второй активный сбор запускать нельзя.",
            "inputSchema" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "username" to mapOf("type" to "string"),
                    "intervalSeconds" to mapOf("type" to "integer"),
                    "intervalMinutes" to mapOf("type" to "number"),
                    "durationHours" to mapOf("type" to "integer"),
                    "metric" to mapOf("type" to "string"),
                    "title" to mapOf("type" to "string")
                ),
                "required" to listOf("username")
            )
        ),
        mapOf(
            "name" to "github_get_user_stars_stats",
            "description" to "Получить накопленную статистику единственного запущенного (или последнего) сбора.",
            "inputSchema" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "username" to mapOf("type" to "string"),
                    "period" to mapOf("type" to "string"),
                    "includeTimestamps" to mapOf("type" to "boolean")
                ),
                "required" to emptyList<String>()
            )
        ),
        mapOf(
            "name" to "github_stop_user_stars_tracking",
            "description" to "Остановить единственный активный периодический сбор статистики.",
            "inputSchema" to mapOf(
                "type" to "object",
                "properties" to emptyMap<String, Any?>(),
                "required" to emptyList<String>()
            )
        ),
        mapOf(
            "name" to "summarize_github_user_profile",
            "description" to "Сформировать краткую сводку профиля GitHub на основе результата github_get_user.",
            "inputSchema" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "userJson" to mapOf(
                        "description" to "JSON профиля пользователя GitHub (object или string)",
                        "oneOf" to listOf(
                            mapOf("type" to "object"),
                            mapOf("type" to "string")
                        )
                    )
                ),
                "required" to listOf("userJson")
            )
        ),
        mapOf(
            "name" to "save_summary_to_file",
            "description" to "Сохранить готовую текстовую сводку профиля локально на Android устройстве.",
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

    suspend fun callTool(name: String, arguments: Map<String, Any?>): Map<String, Any?> {
        return when (name) {
            "github_get_user" -> {
                val username = arguments["username"]?.toString().orEmpty()
                if (username.isBlank()) {
                    errorResult("username is required")
                } else {
                    githubApiClient.getUser(username)
                        .fold(
                            onSuccess = { user ->
                                successResult(
                                    text = "GitHub user ${user["login"]} (${user["name"] ?: "n/a"}), public repos: ${user["public_repos"]}",
                                    structured = user
                                )
                            },
                            onFailure = { errorResult(it.message ?: "GitHub user request failed") }
                        )
                }
            }

            "github_get_repo" -> {
                val owner = arguments["owner"]?.toString().orEmpty()
                val repo = arguments["repo"]?.toString().orEmpty()
                if (owner.isBlank() || repo.isBlank()) {
                    errorResult("owner and repo are required")
                } else {
                    githubApiClient.getRepo(owner, repo)
                        .fold(
                            onSuccess = { repository ->
                                successResult(
                                    text = "Repo ${repository["full_name"]}, stars: ${repository["stargazers_count"]}, open issues: ${repository["open_issues_count"]}",
                                    structured = repository
                                )
                            },
                            onFailure = { errorResult(it.message ?: "GitHub repo request failed") }
                        )
                }
            }

            "github_list_repo_issues" -> {
                val owner = arguments["owner"]?.toString().orEmpty()
                val repo = arguments["repo"]?.toString().orEmpty()
                if (owner.isBlank() || repo.isBlank()) {
                    errorResult("owner and repo are required")
                } else {
                    githubApiClient.listRepoIssues(owner, repo)
                        .fold(
                            onSuccess = { issues ->
                                val topIssues = issues.take(5)
                                val summary = if (topIssues.isEmpty()) {
                                    "No open issues."
                                } else {
                                    topIssues.joinToString("\n") {
                                        "#${it["number"]}: ${it["title"]}"
                                    }
                                }
                                successResult(
                                    text = summary,
                                    structured = mapOf("issues" to topIssues)
                                )
                            },
                            onFailure = { errorResult(it.message ?: "GitHub issues request failed") }
                        )
                }
            }

            "github_schedule_user_stars_tracking" -> {
                val username = arguments["username"]?.toString().orEmpty()
                val explicitIntervalSeconds = parseNumber(arguments["intervalSeconds"])?.toInt()
                val intervalMinutesRaw = parseNumber(arguments["intervalMinutes"])
                val intervalSecondsFromMinutes = intervalMinutesRaw
                    ?.takeIf { it > 0.0 }
                    ?.let { (it * 60.0).roundToInt() }
                    ?.takeIf { it > 0 }
                val intervalMinutesInt = intervalMinutesRaw
                    ?.takeIf { it >= 1.0 && it % 1.0 == 0.0 }
                    ?.toInt()
                val durationHours = (arguments["durationHours"] as? Number)?.toInt()
                val metric = arguments["metric"]?.toString()
                val title = arguments["title"]?.toString()

                val result = githubTrackingTools.scheduleUserMetricTracking(
                    username = username,
                    intervalSeconds = explicitIntervalSeconds ?: intervalSecondsFromMinutes,
                    intervalMinutes = intervalMinutesInt,
                    durationHours = durationHours,
                    metric = metric,
                    title = title
                )
                if (result.isError) {
                    errorResult(result.text)
                } else {
                    successResult(result.text, result.structured)
                }
            }

            "github_get_user_stars_stats" -> {
                val username = arguments["username"]?.toString()
                val period = arguments["period"]?.toString()
                val includeTimestamps = arguments["includeTimestamps"] as? Boolean
                    ?: (arguments["includeTimestamps"] as? String)?.toBooleanStrictOrNull()

                val result = githubTrackingTools.getUserMetricStats(
                    trackingId = null,
                    username = username,
                    period = period,
                    includeTimestamps = includeTimestamps
                )
                if (result.isError) {
                    errorResult(result.text)
                } else {
                    successResult(result.text, result.structured)
                }
            }

            "github_stop_user_stars_tracking" -> {
                val result = githubTrackingTools.stopUserMetricTracking(null)
                if (result.isError) {
                    errorResult(result.text)
                } else {
                    successResult(result.text, result.structured)
                }
            }

            "summarize_github_user_profile" -> {
                val userMap = normalizeGithubUser(arguments["userJson"])
                    ?: return errorResult("userJson is required and must contain GitHub user JSON")

                val login = userMap["login"].asText()
                val name = userMap["name"].asText()
                val publicRepos = userMap["public_repos"].asLong()
                val followers = userMap["followers"].asLong()
                val following = userMap["following"].asLong()
                val bio = userMap["bio"].asText()
                val createdAt = userMap["created_at"].asText()

                val summaryText = buildString {
                    append("GitHub профиль ")
                    append(login.ifBlank { "unknown" })
                    if (name.isNotBlank()) append(" ($name)")
                    append(". ")
                    append("Публичные репозитории: ${publicRepos ?: "n/a"}, ")
                    append("подписчики: ${followers ?: "n/a"}, ")
                    append("подписки: ${following ?: "n/a"}.")
                    if (bio.isNotBlank()) append(" Bio: $bio.")
                    if (createdAt.isNotBlank()) append(" Аккаунт создан: $createdAt.")
                }

                val structured = mapOf(
                    "login" to login,
                    "name" to name,
                    "publicRepos" to publicRepos,
                    "followers" to followers,
                    "following" to following,
                    "bio" to bio,
                    "createdAt" to createdAt,
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

    private fun parseNumber(value: Any?): Double? {
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }
    }

    private fun normalizeGithubUser(value: Any?): Map<String, Any?>? {
        return when (value) {
            is Map<*, *> -> value.entries
                .filter { it.key != null }
                .associate { it.key.toString() to it.value }
            is String -> parseJsonMap(value)
            else -> null
        }
    }

    private fun parseJsonMap(raw: String): Map<String, Any?>? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        return runCatching { anyMapAdapter.fromJson(trimmed) }.getOrNull()
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
