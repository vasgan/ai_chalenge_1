package com.example.vasganchalenge1.data.toolrouting

import com.example.vasganchalenge1.data.pipeline.McpPipelineDescriptor
import com.example.vasganchalenge1.data.repositories.McpTool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ToolRouterResponseParser(
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    fun parse(
        raw: String,
        availableTools: List<McpTool>,
        availablePipelines: List<McpPipelineDescriptor>
    ): ToolResolution {
        val jsonText = extractJsonObject(raw) ?: return ToolResolution.NoTool
        val root = runCatching { json.parseToJsonElement(jsonText).jsonObject }.getOrNull()
            ?: return ToolResolution.NoTool

        val action = root["action"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        return when (action) {
            "no_tool" -> ToolResolution.NoTool
            "clarification" -> {
                val message = root["message"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (message.isBlank()) ToolResolution.NoTool else ToolResolution.ClarificationNeeded(message)
            }
            "tool_call" -> parseToolCall(root, availableTools)
            "pipeline_call" -> parsePipelineCall(root, availablePipelines)
            else -> ToolResolution.NoTool
        }
    }

    private fun parseToolCall(root: JsonObject, availableTools: List<McpTool>): ToolResolution {
        val toolName = root["tool"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (toolName.isBlank()) return ToolResolution.NoTool

        val tool = availableTools.firstOrNull { it.name == toolName } ?: return ToolResolution.NoTool
        val arguments = root["arguments"] as? JsonObject ?: return ToolResolution.NoTool

        val missingRequired = tool.requiredParams.filter { required ->
            isMissingArgument(arguments[required])
        }
        if (missingRequired.isNotEmpty()) {
            return ToolResolution.ClarificationNeeded(
                "Уточни параметры: ${missingRequired.joinToString(", ")}."
            )
        }

        return ToolResolution.ToolCall(
            toolName = toolName,
            argumentsJson = arguments.toString()
        )
    }

    private fun parsePipelineCall(
        root: JsonObject,
        availablePipelines: List<McpPipelineDescriptor>
    ): ToolResolution {
        val pipelineName = root["pipeline"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (pipelineName.isBlank()) return ToolResolution.NoTool

        val pipeline = availablePipelines.firstOrNull { it.name == pipelineName } ?: return ToolResolution.NoTool
        val arguments = root["arguments"] as? JsonObject ?: return ToolResolution.NoTool

        val missingRequired = pipeline.requiredArgs.filter { required ->
            isMissingArgument(arguments[required])
        }
        if (missingRequired.isNotEmpty()) {
            return ToolResolution.ClarificationNeeded(
                "Уточни параметры pipeline: ${missingRequired.joinToString(", ")}."
            )
        }

        return ToolResolution.PipelineCall(
            pipelineName = pipelineName,
            argumentsJson = arguments.toString()
        )
    }

    private fun isMissingArgument(value: JsonElement?): Boolean {
        if (value == null) return true
        return when (value) {
            is JsonPrimitive -> value.contentOrNull?.isBlank() != false
            else -> false
        }
    }

    private fun extractJsonObject(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null

        val withoutFence = trimmed
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val start = withoutFence.indexOf('{')
        val end = withoutFence.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return withoutFence.substring(start, end + 1)
    }
}
