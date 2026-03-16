package com.example.vasganchalenge1.ui

import com.example.vasganchalenge1.data.toolrouting.ToolResolution

internal data class McpToolCommand(
    val name: String,
    val argumentsJson: String,
    val serverId: String? = null
)

internal data class McpPipelineCommand(
    val name: String,
    val argumentsJson: String
)

internal enum class InitialChatRoute {
    DIRECT_TOOL,
    DIRECT_PIPELINE,
    NATURAL_LANGUAGE
}

internal enum class RoutedChatAction {
    NORMAL_CHAT,
    EXECUTE_TOOL,
    EXECUTE_PIPELINE,
    ASK_CLARIFICATION
}

internal fun initialChatRoute(input: String): InitialChatRoute {
    return when {
        parseMcpToolCommand(input) != null -> InitialChatRoute.DIRECT_TOOL
        parseMcpPipelineCommand(input) != null -> InitialChatRoute.DIRECT_PIPELINE
        else -> InitialChatRoute.NATURAL_LANGUAGE
    }
}

internal fun routedChatAction(resolution: ToolResolution): RoutedChatAction {
    return when (resolution) {
        ToolResolution.NoTool -> RoutedChatAction.NORMAL_CHAT
        is ToolResolution.ToolCall -> RoutedChatAction.EXECUTE_TOOL
        is ToolResolution.PipelineCall -> RoutedChatAction.EXECUTE_PIPELINE
        is ToolResolution.ClarificationNeeded -> RoutedChatAction.ASK_CLARIFICATION
    }
}

internal fun parseMcpToolCommand(input: String): McpToolCommand? {
    val trimmed = input.trim()
    if (!trimmed.startsWith("/tool ")) return null
    val payload = trimmed.removePrefix("/tool ").trim()
    if (payload.isBlank()) return null

    val firstSpace = payload.indexOf(' ')
    val rawToolName = if (firstSpace >= 0) payload.substring(0, firstSpace).trim() else payload
    val argsJson = if (firstSpace >= 0) payload.substring(firstSpace + 1).trim().ifBlank { "{}" } else "{}"
    if (rawToolName.isBlank()) return null

    val serverId = rawToolName.substringBefore(':').takeIf { rawToolName.contains(':') }?.trim()?.ifBlank { null }
    val toolName = rawToolName.substringAfter(':', rawToolName).trim()
    if (toolName.isBlank()) return null

    return McpToolCommand(name = toolName, argumentsJson = argsJson, serverId = serverId)
}

internal fun parseMcpPipelineCommand(input: String): McpPipelineCommand? {
    val trimmed = input.trim()
    if (!trimmed.startsWith("/pipeline ")) return null
    val payload = trimmed.removePrefix("/pipeline ").trim()
    if (payload.isBlank()) return null

    val firstSpace = payload.indexOf(' ')
    val pipelineName = if (firstSpace >= 0) payload.substring(0, firstSpace).trim() else payload
    val argsJson = if (firstSpace >= 0) payload.substring(firstSpace + 1).trim().ifBlank { "{}" } else "{}"
    if (pipelineName.isBlank()) return null

    return McpPipelineCommand(name = pipelineName, argumentsJson = argsJson)
}
