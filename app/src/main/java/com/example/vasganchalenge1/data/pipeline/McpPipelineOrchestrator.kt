package com.example.vasganchalenge1.data.pipeline

import com.example.vasganchalenge1.data.repositories.McpRepository
import com.example.vasganchalenge1.data.repositories.McpTool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class McpPipelineOrchestrator @Inject constructor(
    private val mcpRepository: McpRepository
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun availablePipelines(availableTools: List<McpTool>): List<McpPipelineDescriptor> {
        val toolNames = availableTools.map { it.name }.toSet()
        return McpPipelineCatalog.availableFor(toolNames)
    }

    suspend fun execute(
        pipelineName: String,
        argumentsJson: String
    ): PipelineExecutionResult {
        val available = availablePipelines(mcpRepository.state.value.tools)
        val descriptor = available.firstOrNull { it.name == pipelineName }
            ?: return PipelineExecutionResult(
                success = false,
                pipelineName = pipelineName,
                steps = emptyList(),
                finalMessage = "Pipeline не найден или недоступен: $pipelineName"
            )

        val args = parseArgs(argumentsJson)
        return when (descriptor.name) {
            McpPipelineCatalog.githubUserSummaryAndSave.name ->
                executeGithubUserSummaryAndSave(descriptor.name, args)
            McpPipelineCatalog.githubUserTrackingFlow.name ->
                executeGithubUserTrackingFlow(descriptor.name, args)
            else -> PipelineExecutionResult(
                success = false,
                pipelineName = descriptor.name,
                steps = emptyList(),
                finalMessage = "Pipeline не реализован: ${descriptor.name}"
            )
        }
    }

    private suspend fun executeGithubUserSummaryAndSave(
        pipelineName: String,
        args: JsonObject
    ): PipelineExecutionResult {
        val username = args["username"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (username.isBlank()) {
            return PipelineExecutionResult(
                success = false,
                pipelineName = pipelineName,
                steps = emptyList(),
                finalMessage = "Для pipeline $pipelineName нужен параметр username"
            )
        }

        val steps = mutableListOf<PipelineStepResult>()

        val step1 = executeStep(
            stepName = "Fetch GitHub user",
            toolName = "github_get_user",
            argumentsJson = buildJsonObject { put("username", username) }.toString()
        )
        steps += step1
        if (!step1.success) {
            return PipelineExecutionResult(
                success = false,
                pipelineName = pipelineName,
                steps = steps,
                finalMessage = "Pipeline остановлен на шаге github_get_user: ${step1.errorMessage ?: step1.textResult.orEmpty()}"
            )
        }

        val rawUserJson = step1.structuredResult ?: "{}"
        val step2 = executeStep(
            stepName = "Summarize GitHub profile",
            toolName = "summarize_github_user_profile",
            argumentsJson = buildJsonObject {
                put("userJson", rawUserJson)
            }.toString()
        )
        steps += step2
        if (!step2.success) {
            return PipelineExecutionResult(
                success = false,
                pipelineName = pipelineName,
                steps = steps,
                finalMessage = "Pipeline остановлен на шаге summarize_github_user_profile: ${step2.errorMessage ?: step2.textResult.orEmpty()}"
            )
        }

        val summaryText = step2.textResult.orEmpty().ifBlank { "GitHub summary for $username" }
        val step3 = executeStep(
            stepName = "Save summary locally",
            toolName = "save_summary_to_file",
            argumentsJson = buildJsonObject {
                put("title", "GitHub summary for $username")
                put("summaryText", summaryText)
                put("rawJson", rawUserJson)
            }.toString()
        )
        steps += step3
        if (!step3.success) {
            return PipelineExecutionResult(
                success = false,
                pipelineName = pipelineName,
                steps = steps,
                finalMessage = "Pipeline остановлен на шаге save_summary_to_file: ${step3.errorMessage ?: step3.textResult.orEmpty()}"
            )
        }

        return PipelineExecutionResult(
            success = true,
            pipelineName = pipelineName,
            steps = steps,
            finalMessage = step3.textResult ?: "Pipeline $pipelineName завершен успешно"
        )
    }

    private suspend fun executeGithubUserTrackingFlow(
        pipelineName: String,
        args: JsonObject
    ): PipelineExecutionResult {
        val username = args.string("username").orEmpty().trim()
        if (username.isBlank()) {
            return PipelineExecutionResult(
                success = false,
                pipelineName = pipelineName,
                steps = emptyList(),
                finalMessage = "Для pipeline $pipelineName нужен параметр username"
            )
        }

        val steps = mutableListOf<PipelineStepResult>()

        val scheduleArgs = buildJsonObject {
            put("username", username)
            args.int("intervalSeconds")?.let { put("intervalSeconds", it) }
            args.double("intervalMinutes")?.let { put("intervalMinutes", it) }
            args.int("durationHours")?.let { put("durationHours", it) }
            args.string("metric")?.takeIf { it.isNotBlank() }?.let { put("metric", it) }
            args.string("title")?.takeIf { it.isNotBlank() }?.let { put("title", it) }
        }
        val step1 = executeStep(
            stepName = "Start tracking",
            toolName = "github_schedule_user_stars_tracking",
            argumentsJson = scheduleArgs.toString()
        )
        steps += step1
        if (!step1.success) {
            return PipelineExecutionResult(
                success = false,
                pipelineName = pipelineName,
                steps = steps,
                finalMessage = "Pipeline остановлен на шаге github_schedule_user_stars_tracking: ${step1.errorMessage ?: step1.textResult.orEmpty()}"
            )
        }

        val statsArgs = buildJsonObject {
            put("username", username)
            args.string("period")?.takeIf { it.isNotBlank() }?.let { put("period", it) }
            args.bool("includeTimestamps")?.let { put("includeTimestamps", it) }
        }
        val step2 = executeStep(
            stepName = "Get tracking stats",
            toolName = "github_get_user_stars_stats",
            argumentsJson = statsArgs.toString()
        )
        steps += step2
        if (!step2.success) {
            return PipelineExecutionResult(
                success = false,
                pipelineName = pipelineName,
                steps = steps,
                finalMessage = "Pipeline остановлен на шаге github_get_user_stars_stats: ${step2.errorMessage ?: step2.textResult.orEmpty()}"
            )
        }

        val shouldStop = args.bool("stopAfterStats") == true
        if (shouldStop) {
            val hasStopTool = mcpRepository.state.value.tools.any { it.name == "github_stop_user_stars_tracking" }
            if (hasStopTool) {
                val step3 = executeStep(
                    stepName = "Stop tracking",
                    toolName = "github_stop_user_stars_tracking",
                    argumentsJson = "{}"
                )
                steps += step3
                if (!step3.success) {
                    return PipelineExecutionResult(
                        success = false,
                        pipelineName = pipelineName,
                        steps = steps,
                        finalMessage = "Pipeline остановлен на шаге github_stop_user_stars_tracking: ${step3.errorMessage ?: step3.textResult.orEmpty()}"
                    )
                }
                return PipelineExecutionResult(
                    success = true,
                    pipelineName = pipelineName,
                    steps = steps,
                    finalMessage = step3.textResult ?: "Pipeline $pipelineName завершен успешно"
                )
            }
        }

        return PipelineExecutionResult(
            success = true,
            pipelineName = pipelineName,
            steps = steps,
            finalMessage = step2.textResult ?: "Pipeline $pipelineName завершен успешно"
        )
    }

    private suspend fun executeStep(
        stepName: String,
        toolName: String,
        argumentsJson: String
    ): PipelineStepResult {
        val response = runCatching { mcpRepository.callTool(toolName, argumentsJson).getOrThrow() }
            .getOrElse { throwable ->
                return PipelineStepResult(
                    stepName = stepName,
                    toolName = toolName,
                    success = false,
                    textResult = null,
                    structuredResult = null,
                    errorMessage = throwable.message ?: "Tool execution failed"
                )
            }

        if (response.isError) {
            return PipelineStepResult(
                stepName = stepName,
                toolName = toolName,
                success = false,
                textResult = response.text,
                structuredResult = response.structuredJson,
                errorMessage = response.text.ifBlank { "Tool returned error" }
            )
        }

        return PipelineStepResult(
            stepName = stepName,
            toolName = toolName,
            success = true,
            textResult = response.text,
            structuredResult = response.structuredJson,
            errorMessage = null
        )
    }

    private fun parseArgs(argumentsJson: String): JsonObject {
        val trimmed = argumentsJson.trim().ifBlank { "{}" }
        return runCatching { json.parseToJsonElement(trimmed) as? JsonObject }
            .getOrNull() ?: buildJsonObject { }
    }

    private fun JsonObject.string(key: String): String? {
        return this[key]?.jsonPrimitive?.contentOrNull
    }

    private fun JsonObject.int(key: String): Int? {
        return this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
    }

    private fun JsonObject.double(key: String): Double? {
        return this[key]?.jsonPrimitive?.contentOrNull?.replace(',', '.')?.toDoubleOrNull()
    }

    private fun JsonObject.bool(key: String): Boolean? {
        val raw = this[key]?.jsonPrimitive?.contentOrNull ?: return null
        return raw.equals("true", ignoreCase = true)
    }
}
