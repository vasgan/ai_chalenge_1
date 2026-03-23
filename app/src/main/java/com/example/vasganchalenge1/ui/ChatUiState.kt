package com.example.vasganchalenge1.ui

import com.example.vasganchalenge1.data.LongTermMode
import com.example.vasganchalenge1.data.MemoryField
import com.example.vasganchalenge1.data.RunMetric
import com.example.vasganchalenge1.data.UiChatMessage
import com.example.vasganchalenge1.data.repositories.ModelType
import com.example.vasganchalenge1.data.taskfsm.TaskState
import com.example.vasganchalenge1.rag.model.RagQualityMode

enum class ToolWorkMode {
    IDLE,
    TOOL_CALL_IN_PROGRESS,
    PIPELINE_IN_PROGRESS
}

data class McpServerDebugInfo(
    val serverId: String,
    val label: String,
    val status: String,
    val toolsCount: Int
)

data class PipelineStepDebugInfo(
    val index: Int,
    val stepName: String,
    val serverId: String,
    val toolName: String,
    val status: String,
    val message: String = ""
)

data class ChatUiState(
    val chatId: String = "",
    val profileId: String = "",
    val profileTitle: String = "",
    val taskId: String = "",
    val taskTitle: String = "",
    val rootChatId: String = "",
    val parentChatId: String? = null,
    val branchedFromMessageId: Long? = null,
    val title: String = "",
    val facts: String = "",
    val longTermMode: LongTermMode = LongTermMode.MANUAL,
    val profileDescription: String = "",
    val communicationLanguage: String = "",
    val longTermFields: List<MemoryField> = emptyList(),
    val invariants: List<String> = emptyList(),
    val workingMemoryContext: String = "",
    val taskStateDebug: TaskState? = null,
    val factsMessageCount: Int = 0,
    val mcpConnectionStatus: String = "DISCONNECTED",
    val mcpServerUrl: String = "",
    val mcpToolsCount: Int = 0,
    val mcpServers: List<McpServerDebugInfo> = emptyList(),
    val selectedModelType: ModelType = ModelType.CLOUD,
    val showModelPicker: Boolean = true,
    val ragEnabled: Boolean = false,
    val ragQualityMode: RagQualityMode = RagQualityMode.IMPROVED,
    val ragTopKBefore: Int = 8,
    val ragTopKAfter: Int = 4,
    val ragSimilarityThreshold: Float = 0.55f,
    val toolWorkMode: ToolWorkMode = ToolWorkMode.IDLE,
    val activeToolServerId: String? = null,
    val activeToolServerLabel: String? = null,
    val activeToolName: String? = null,
    val activePipelineName: String? = null,
    val activePipelineSteps: List<PipelineStepDebugInfo> = emptyList(),
    val recentToolActivities: List<String> = emptyList(),
    val input: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val messages: List<UiChatMessage> = emptyList(),
    val metrics: List<RunMetric> = emptyList()
)
