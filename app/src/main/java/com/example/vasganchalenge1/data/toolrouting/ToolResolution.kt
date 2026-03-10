package com.example.vasganchalenge1.data.toolrouting

sealed interface ToolResolution {
    data object NoTool : ToolResolution

    data class ToolCall(
        val toolName: String,
        val argumentsJson: String
    ) : ToolResolution

    data class ClarificationNeeded(
        val message: String
    ) : ToolResolution
}
