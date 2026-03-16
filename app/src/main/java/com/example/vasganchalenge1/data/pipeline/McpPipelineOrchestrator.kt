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

    fun findPipeline(
        pipelineName: String,
        availableTools: List<McpTool>
    ): McpPipelineDescriptor? {
        return availablePipelines(availableTools).firstOrNull { it.name == pipelineName }
    }

    suspend fun execute(
        pipelineName: String,
        argumentsJson: String,
        onProgress: ((List<PipelineStepResult>) -> Unit)? = null
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
            McpPipelineCatalog.crossServerGithubReportFlow.name,
            McpPipelineCatalog.githubUserSummaryAndSave.name ->
                executeCrossServerGithubReportFlow(
                    descriptor = descriptor,
                    args = args,
                    onProgress = onProgress
                )

            McpPipelineCatalog.githubUserTrackingFlow.name ->
                executeGithubUserTrackingFlow(
                    descriptor = descriptor,
                    args = args,
                    onProgress = onProgress
                )

            else -> PipelineExecutionResult(
                success = false,
                pipelineName = descriptor.name,
                steps = emptyList(),
                finalMessage = "Pipeline не реализован: ${descriptor.name}"
            )
        }
    }

    private suspend fun executeCrossServerGithubReportFlow(
        descriptor: McpPipelineDescriptor,
        args: JsonObject,
        onProgress: ((List<PipelineStepResult>) -> Unit)?
    ): PipelineExecutionResult {
        val pipelineName = descriptor.name
        val username = args.string("username").orEmpty().trim()
        val repo = args.string("repo").orEmpty().trim()
        if (username.isBlank() || repo.isBlank()) {
            return PipelineExecutionResult(
                success = false,
                pipelineName = pipelineName,
                steps = emptyList(),
                finalMessage = "Для pipeline $pipelineName нужны параметры username и repo"
            )
        }

        val steps = mutableListOf<PipelineStepResult>()
        fun push(step: PipelineStepResult) {
            steps += step
            onProgress?.invoke(steps.toList())
        }

        val step1Def = descriptor.steps[0]
        val step1 = executeStep(
            stepDef = step1Def,
            argumentsJson = buildJsonObject { put("username", username) }.toString()
        )
        push(step1)
        if (!step1.success) {
            return PipelineExecutionResult(
                success = false,
                pipelineName = pipelineName,
                steps = steps,
                finalMessage = "Pipeline остановлен на шаге ${step1.toolName}: ${step1.errorMessage ?: step1.textResult.orEmpty()}"
            )
        }

        val step2Def = descriptor.steps[1]
        val step2 = executeStep(
            stepDef = step2Def,
            argumentsJson = buildJsonObject {
                put("owner", username)
                put("repo", repo)
            }.toString()
        )
        push(step2)
        if (!step2.success) {
            return PipelineExecutionResult(
                success = false,
                pipelineName = pipelineName,
                steps = steps,
                finalMessage = "Pipeline остановлен на шаге ${step2.toolName}: ${step2.errorMessage ?: step2.textResult.orEmpty()}"
            )
        }

        val step3Def = descriptor.steps[2]
        val step3 = executeStep(
            stepDef = step3Def,
            argumentsJson = buildJsonObject {
                put("owner", username)
                put("repo", repo)
            }.toString()
        )
        push(step3)
        if (!step3.success) {
            return PipelineExecutionResult(
                success = false,
                pipelineName = pipelineName,
                steps = steps,
                finalMessage = "Pipeline остановлен на шаге ${step3.toolName}: ${step3.errorMessage ?: step3.textResult.orEmpty()}"
            )
        }

        val step4Def = descriptor.steps[3]
        val step4 = executeStep(
            stepDef = step4Def,
            argumentsJson = buildJsonObject {
                put("userJson", step1.structuredResult ?: "{}")
                put("repoJson", step2.structuredResult ?: "{}")
                put("issuesJson", extractIssuesJson(step3.structuredResult))
            }.toString()
        )
        push(step4)
        if (!step4.success) {
            return PipelineExecutionResult(
                success = false,
                pipelineName = pipelineName,
                steps = steps,
                finalMessage = "Pipeline остановлен на шаге ${step4.toolName}: ${step4.errorMessage ?: step4.textResult.orEmpty()}"
            )
        }

        val step5Def = descriptor.steps[4]
        val step5 = executeStep(
            stepDef = step5Def,
            argumentsJson = buildJsonObject {
                put("title", "GitHub report for $username/$repo")
                put("summaryText", step4.textResult.orEmpty())
                put(
                    "rawJson",
                    buildJsonObject {
                        put("user", step1.structuredResult ?: "{}")
                        put("repo", step2.structuredResult ?: "{}")
                        put("issues", extractIssuesJson(step3.structuredResult))
                    }.toString()
                )
            }.toString()
        )
        push(step5)
        if (!step5.success) {
            return PipelineExecutionResult(
                success = false,
                pipelineName = pipelineName,
                steps = steps,
                finalMessage = "Pipeline остановлен на шаге ${step5.toolName}: ${step5.errorMessage ?: step5.textResult.orEmpty()}"
            )
        }

        return PipelineExecutionResult(
            success = true,
            pipelineName = pipelineName,
            steps = steps,
            finalMessage = step5.textResult ?: "Pipeline $pipelineName завершен успешно"
        )
    }

    private suspend fun executeGithubUserTrackingFlow(
        descriptor: McpPipelineDescriptor,
        args: JsonObject,
        onProgress: ((List<PipelineStepResult>) -> Unit)?
    ): PipelineExecutionResult {
        val pipelineName = descriptor.name
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
        fun push(step: PipelineStepResult) {
            steps += step
            onProgress?.invoke(steps.toList())
        }

        val startDef = descriptor.steps[0]
        val startStep = executeStep(
            stepDef = startDef,
            argumentsJson = buildJsonObject {
                put("username", username)
                args.int("intervalSeconds")?.let { put("intervalSeconds", it) }
                args.double("intervalMinutes")?.let { put("intervalMinutes", it) }
                args.int("durationHours")?.let { put("durationHours", it) }
                args.string("metric")?.takeIf { it.isNotBlank() }?.let { put("metric", it) }
                args.string("title")?.takeIf { it.isNotBlank() }?.let { put("title", it) }
            }.toString()
        )
        push(startStep)
        if (!startStep.success) {
            return PipelineExecutionResult(
                success = false,
                pipelineName = pipelineName,
                steps = steps,
                finalMessage = "Pipeline остановлен на шаге ${startStep.toolName}: ${startStep.errorMessage ?: startStep.textResult.orEmpty()}"
            )
        }

        val statsDef = descriptor.steps[1]
        val statsStep = executeStep(
            stepDef = statsDef,
            argumentsJson = buildJsonObject {
                put("username", username)
                args.string("period")?.takeIf { it.isNotBlank() }?.let { put("period", it) }
                args.bool("includeTimestamps")?.let { put("includeTimestamps", it) }
            }.toString()
        )
        push(statsStep)
        if (!statsStep.success) {
            return PipelineExecutionResult(
                success = false,
                pipelineName = pipelineName,
                steps = steps,
                finalMessage = "Pipeline остановлен на шаге ${statsStep.toolName}: ${statsStep.errorMessage ?: statsStep.textResult.orEmpty()}"
            )
        }

        val shouldStop = args.bool("stopAfterStats") == true
        if (!shouldStop) {
            return PipelineExecutionResult(
                success = true,
                pipelineName = pipelineName,
                steps = steps,
                finalMessage = statsStep.textResult ?: "Pipeline $pipelineName завершен успешно"
            )
        }

        val stopDef = descriptor.steps[2]
        val stopStep = executeStep(
            stepDef = stopDef,
            argumentsJson = "{}"
        )
        push(stopStep)
        if (!stopStep.success) {
            return PipelineExecutionResult(
                success = false,
                pipelineName = pipelineName,
                steps = steps,
                finalMessage = "Pipeline остановлен на шаге ${stopStep.toolName}: ${stopStep.errorMessage ?: stopStep.textResult.orEmpty()}"
            )
        }

        return PipelineExecutionResult(
            success = true,
            pipelineName = pipelineName,
            steps = steps,
            finalMessage = stopStep.textResult ?: "Pipeline $pipelineName завершен успешно"
        )
    }

    private suspend fun executeStep(
        stepDef: PipelineStepDefinition,
        argumentsJson: String
    ): PipelineStepResult {
        val response = runCatching {
            mcpRepository.callTool(
                name = stepDef.toolName,
                argumentsJson = argumentsJson,
                preferredServerId = stepDef.serverId
            ).getOrThrow()
        }.getOrElse { throwable ->
            return PipelineStepResult(
                stepName = stepDef.stepName,
                serverId = stepDef.serverId,
                toolName = stepDef.toolName,
                success = false,
                textResult = null,
                structuredResult = null,
                errorMessage = throwable.message ?: "Tool execution failed"
            )
        }

        if (response.isError) {
            return PipelineStepResult(
                stepName = stepDef.stepName,
                serverId = stepDef.serverId,
                toolName = stepDef.toolName,
                success = false,
                textResult = response.text,
                structuredResult = response.structuredJson,
                errorMessage = response.text.ifBlank { "Tool returned error" }
            )
        }

        return PipelineStepResult(
            stepName = stepDef.stepName,
            serverId = stepDef.serverId,
            toolName = stepDef.toolName,
            success = true,
            textResult = response.text,
            structuredResult = response.structuredJson,
            errorMessage = null
        )
    }

    private fun extractIssuesJson(rawStructured: String?): String {
        if (rawStructured.isNullOrBlank()) return "[]"
        val root = runCatching { json.parseToJsonElement(rawStructured) }.getOrNull() as? JsonObject ?: return "[]"
        val issues = root["issues"]
        return issues?.toString() ?: "[]"
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
