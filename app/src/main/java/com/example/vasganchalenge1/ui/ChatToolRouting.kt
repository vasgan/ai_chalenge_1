package com.example.vasganchalenge1.ui

import com.example.vasganchalenge1.data.toolrouting.ToolResolution

internal data class McpToolCommand(
    val name: String,
    val argumentsJson: String
)

internal enum class InitialChatRoute {
    DIRECT_TOOL,
    NATURAL_LANGUAGE
}

internal enum class RoutedChatAction {
    NORMAL_CHAT,
    EXECUTE_TOOL,
    ASK_CLARIFICATION
}

internal fun initialChatRoute(input: String): InitialChatRoute {
    return if (parseMcpToolCommand(input) != null) {
        InitialChatRoute.DIRECT_TOOL
    } else {
        InitialChatRoute.NATURAL_LANGUAGE
    }
}

internal fun routedChatAction(resolution: ToolResolution): RoutedChatAction {
    return when (resolution) {
        ToolResolution.NoTool -> RoutedChatAction.NORMAL_CHAT
        is ToolResolution.ToolCall -> RoutedChatAction.EXECUTE_TOOL
        is ToolResolution.ClarificationNeeded -> RoutedChatAction.ASK_CLARIFICATION
    }
}

internal fun parseMcpToolCommand(input: String): McpToolCommand? {
    val trimmed = input.trim()
    if (!trimmed.startsWith("/tool ")) return null
    val payload = trimmed.removePrefix("/tool ").trim()
    if (payload.isBlank()) return null

    val firstSpace = payload.indexOf(' ')
    val toolName = if (firstSpace >= 0) payload.substring(0, firstSpace).trim() else payload
    val argsJson = if (firstSpace >= 0) payload.substring(firstSpace + 1).trim().ifBlank { "{}" } else "{}"
    if (toolName.isBlank()) return null

    return McpToolCommand(name = toolName, argumentsJson = argsJson)
}
