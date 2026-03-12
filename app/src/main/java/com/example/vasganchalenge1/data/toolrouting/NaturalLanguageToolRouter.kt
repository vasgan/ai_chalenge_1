package com.example.vasganchalenge1.data.toolrouting

import com.example.vasganchalenge1.data.ChatRequest
import com.example.vasganchalenge1.data.Message
import com.example.vasganchalenge1.data.network.ApiService
import com.example.vasganchalenge1.data.pipeline.McpPipelineDescriptor
import com.example.vasganchalenge1.data.repositories.AppSettings
import com.example.vasganchalenge1.data.repositories.McpTool
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.roundToInt
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NaturalLanguageToolRouter @Inject constructor(
    private val apiService: ApiService
) {
    private val promptBuilder = ToolRouterPromptBuilder()
    private val parser = ToolRouterResponseParser()

    suspend fun resolve(
        settings: AppSettings,
        userMessage: String,
        availableTools: List<McpTool>,
        availablePipelines: List<McpPipelineDescriptor>
    ): ToolResolution {
        if (availableTools.isEmpty() && availablePipelines.isEmpty()) return ToolResolution.NoTool

        val raw = runCatching {
            val response = apiService.chatCompletion(
                ChatRequest(
                    model = settings.model,
                    messages = listOf(
                        Message("system", promptBuilder.buildSystemPrompt(availableTools, availablePipelines)),
                        Message("user", userMessage)
                    ),
                    stop = null,
                    max_tokens = 220,
                    temperature = 0.0
                )
            )
            response.choices.firstOrNull()?.message?.content.orEmpty()
        }.onFailure { throwable ->
            println("NaturalToolRouter request failed: ${throwable.message}")
        }.getOrDefault("")

        if (raw.isNotBlank()) {
            println("NaturalToolRouter raw response: $raw")
        }

        val parsed = parser.parse(raw, availableTools, availablePipelines)
        if (parsed !is ToolResolution.NoTool) {
            println("NaturalToolRouter parsed resolution: $parsed")
            return parsed
        }

        val fallback = fallbackRuleBased(userMessage, availableTools, availablePipelines)
        if (fallback !is ToolResolution.NoTool) {
            println("NaturalToolRouter fallback resolution: $fallback")
        }
        return fallback
    }

    private fun fallbackRuleBased(
        userMessage: String,
        availableTools: List<McpTool>,
        availablePipelines: List<McpPipelineDescriptor>
    ): ToolResolution {
        val normalized = userMessage.lowercase()
        val userSummaryPipeline = availablePipelines.firstOrNull {
            it.name == "github_user_summary_and_save"
        }
        val trackingPipeline = availablePipelines.firstOrNull {
            it.name == "github_user_tracking_flow"
        }
        val scheduleTool = availableTools.firstOrNull { it.name == "github_schedule_user_stars_tracking" }
        val statsTool = availableTools.firstOrNull { it.name == "github_get_user_stars_stats" }
        val stopTool = availableTools.firstOrNull { it.name == "github_stop_user_stars_tracking" }
        val userTool = availableTools.firstOrNull { it.name == "github_get_user" }

        if (userSummaryPipeline != null && isSummaryPipelineRequest(normalized)) {
            val username = extractUsername(userMessage)
                ?: return ToolResolution.ClarificationNeeded("Уточни username GitHub для сводки профиля.")
            return ToolResolution.PipelineCall(
                pipelineName = userSummaryPipeline.name,
                argumentsJson = buildJsonObject {
                    put("username", username)
                }.toString()
            )
        }

        if (trackingPipeline != null && isTrackingPipelineRequest(normalized)) {
            val username = extractUsername(userMessage)
                ?: return ToolResolution.ClarificationNeeded("Уточни username GitHub для запуска tracking pipeline.")
            return ToolResolution.PipelineCall(
                pipelineName = trackingPipeline.name,
                argumentsJson = buildJsonObject {
                    put("username", username)
                    extractIntervalSeconds(userMessage)?.let { put("intervalSeconds", it) }
                    extractDurationHours(userMessage)?.let { put("durationHours", it) }
                    extractStatsPeriod(normalized)?.let { put("period", it) }
                    if (normalized.contains("останов")) {
                        put("stopAfterStats", true)
                    }
                }.toString()
            )
        }

        if (stopTool != null && isStopTrackingRequest(normalized)) {
            return ToolResolution.ToolCall(
                toolName = stopTool.name,
                argumentsJson = "{}"
            )
        }

        if (scheduleTool != null && isScheduleTrackingRequest(normalized)) {
            val username = extractUsername(userMessage)
                ?: return ToolResolution.ClarificationNeeded("Уточни username GitHub для запуска сбора.")
            val args = buildJsonObject {
                put("username", username)
                extractIntervalSeconds(userMessage)?.let { put("intervalSeconds", it) }
                extractDurationHours(userMessage)?.let { put("durationHours", it) }
                when {
                    normalized.contains("public repos") ||
                        normalized.contains("public_repos") ||
                        normalized.contains("публичн") -> put("metric", "public_repos")
                    normalized.contains("звезд") ||
                        normalized.contains("звёзд") ||
                        normalized.contains("stars") -> put("metric", "total_stars")
                }
            }
            return ToolResolution.ToolCall(
                toolName = scheduleTool.name,
                argumentsJson = args.toString()
            )
        }

        if (statsTool != null && isStatsRequest(normalized)) {
            val args = buildJsonObject {
                extractUsername(userMessage)?.let { put("username", it) }
                extractStatsPeriod(normalized)?.let { put("period", it) }
                if (normalized.contains("с метками времени") ||
                    normalized.contains("по времени") ||
                    normalized.contains("точки") ||
                    normalized.contains("timestamps")
                ) {
                    put("includeTimestamps", true)
                }
            }
            return ToolResolution.ToolCall(
                toolName = statsTool.name,
                argumentsJson = args.toString()
            )
        }

        if (userTool != null && normalized.contains("репозитор")) {
            val username = extractUsername(userMessage)
            if (username != null) {
                return ToolResolution.ToolCall(
                    toolName = userTool.name,
                    argumentsJson = "{\"username\":\"$username\"}"
                )
            }
            if (normalized.contains("пользовател")) {
                return ToolResolution.ClarificationNeeded("Уточни username GitHub.")
            }
        }

        return ToolResolution.NoTool
    }

    private fun isSummaryPipelineRequest(normalized: String): Boolean {
        val summaryIntent = normalized.contains("сводк") ||
            normalized.contains("отчет") ||
            normalized.contains("отчёт") ||
            normalized.contains("суммир") ||
            normalized.contains("summary")
        val saveIntent = normalized.contains("сохрани") ||
            normalized.contains("сохран")
        val profileContext = normalized.contains("профил") ||
            normalized.contains("пользовател") ||
            normalized.contains("github")

        return (summaryIntent && saveIntent && profileContext) ||
            (normalized.contains("получи") && normalized.contains("суммир") && saveIntent)
    }

    private fun isTrackingPipelineRequest(normalized: String): Boolean {
        val trackingIntent = normalized.contains("запусти") ||
            normalized.contains("начни") ||
            normalized.contains("собирай") ||
            normalized.contains("трек") ||
            normalized.contains("tracking")
        val explicitNextStep = normalized.contains("и потом") ||
            normalized.contains("а потом") ||
            normalized.contains("затем") ||
            normalized.contains("после этого") ||
            normalized.contains("потом покажи") ||
            normalized.contains("затем покажи")
        val statsIntent = normalized.contains("покажи статист") ||
            normalized.contains("дай статист") ||
            normalized.contains("получи статист") ||
            normalized.contains("report") ||
            normalized.contains("отчет") ||
            normalized.contains("отчёт")
        return trackingIntent && explicitNextStep && statsIntent
    }

    private fun isScheduleTrackingRequest(normalized: String): Boolean {
        val startIntent = normalized.contains("запусти") ||
            normalized.contains("начни") ||
            normalized.contains("включи") ||
            normalized.contains("собирай") ||
            normalized.contains("отслеживай") ||
            normalized.contains("трек") ||
            normalized.contains("tracking")
        val trackingContext = normalized.contains("статист") ||
            normalized.contains("сбор") ||
            normalized.contains("звезд") ||
            normalized.contains("звёзд") ||
            normalized.contains("stars")
        return startIntent && trackingContext
    }

    private fun isStatsRequest(normalized: String): Boolean {
        return normalized.contains("статист") ||
            normalized.contains("сколько собрано") ||
            normalized.contains("накоплен") ||
            normalized.contains("измерен") ||
            normalized.contains("динамик") ||
            normalized.contains("изменени") ||
            normalized.contains("покажи точки") ||
            normalized.contains("points")
    }

    private fun isStopTrackingRequest(normalized: String): Boolean {
        val stopIntent = normalized.contains("останови") ||
            normalized.contains("остановить") ||
            normalized.contains("стоп") ||
            normalized.contains("прекрати") ||
            normalized.contains("выключи") ||
            normalized.contains("stop")
        val trackingContext = normalized.contains("сбор") ||
            normalized.contains("статист") ||
            normalized.contains("трек") ||
            normalized.contains("отслеж") ||
            normalized.contains("stars") ||
            normalized.contains("звезд") ||
            normalized.contains("звёзд")
        return stopIntent && trackingContext
    }

    private fun extractUsername(text: String): String? {
        val regexByWords = Regex(
            pattern = "(?i)(?:у\\s+пользователя|пользовател(?:ь|я|ю)|user|username)\\s+([A-Za-z0-9-]{1,39})"
        )
        regexByWords.find(text)?.groupValues?.getOrNull(1)?.let { return it }

        val regexByGithubProfileUrl = Regex(pattern = "(?i)github\\.com/([A-Za-z0-9-]{1,39})")
        return regexByGithubProfileUrl.find(text)?.groupValues?.getOrNull(1)
    }

    private fun extractIntervalSeconds(text: String): Int? {
        val secondsRegex = Regex(
            "(?i)(?:кажд(?:ые|ую|ый)?|every|раз\\s+в)\\s*(\\d+(?:[\\.,]\\d+)?)\\s*(?:сек(?:унд(?:а|ы)?)?|sec|seconds|s)"
        )
        secondsRegex.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
            ?.let { return it.roundToInt().coerceAtLeast(1) }

        val minutesRegex = Regex(
            "(?i)(?:кажд(?:ые|ую|ый)?|every|раз\\s+в)\\s*(\\d+(?:[\\.,]\\d+)?)\\s*(?:мин(?:ут(?:а|ы)?)?|minutes?|min|m)"
        )
        minutesRegex.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
            ?.let { return (it * 60.0).roundToInt().coerceAtLeast(1) }

        val hoursRegex = Regex(
            "(?i)(?:кажд(?:ые|ую|ый)?|every|раз\\s+в)\\s*(\\d+(?:[\\.,]\\d+)?)\\s*(?:час(?:а|ов)?|hours?|hr|h)"
        )
        hoursRegex.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
            ?.let { return (it * 3600.0).roundToInt().coerceAtLeast(1) }

        return null
    }

    private fun extractDurationHours(text: String): Int? {
        val regex = Regex(
            "(?i)(?:в\\s+течение|на\\s+протяжении|на)\\s*(\\d{1,3})\\s*(?:час(?:а|ов)?|hours?|hr|h)"
        )
        return regex.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1)
    }

    private fun extractStatsPeriod(normalized: String): String? {
        if (normalized.contains("за день") || normalized.contains("за сутки")) return "day"

        val hourMatch = Regex("(?i)за\\s*(\\d{1,3})\\s*час(?:а|ов)?").find(normalized)
        if (hourMatch != null) {
            val hours = hourMatch.groupValues.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: return null
            return "${hours}h"
        }

        val dayMatch = Regex("(?i)за\\s*(\\d{1,3})\\s*(?:дн(?:я|ей)?|д)").find(normalized)
        if (dayMatch != null) {
            val days = dayMatch.groupValues.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: return null
            return "${days}d"
        }
        return null
    }
}
