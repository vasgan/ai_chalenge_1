package com.example.vasganchalenge1.data

import com.example.vasganchalenge1.data.repositories.AppSettings
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val stop: String?,
    val max_tokens: Int?,
    val temperature: Double?
)

data class Message(
    val role: String,
    val content: String
)

data class ChatResponse(
    val choices: List<Choice>,
    val usage: Usage?
)

data class Usage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
)

data class Choice(
    val message: Message
)

data class RunMetric(
    val model: String,
    val latencyMs: Long,
    val totalTokens: Int,
    val totalUsageToken: Int,
    val costUsd: Double?
)

data class MemoryField(
    val key: String,
    val value: String
)

enum class LongTermMode {
    MANUAL, AUTO
}

data class LongTermMemory(
    val mode: LongTermMode = LongTermMode.MANUAL,
    val profileDescription: String = "",
    val communicationLanguage: String = "",
    val customFields: List<MemoryField> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class LongTermMemoryPatch(
    val setProfileDescription: String? = null,
    val setCommunicationLanguage: String? = null,
    val putCustomFields: Map<String, String> = emptyMap(),
    val removeCustomFields: List<String> = emptyList(),
    val clearAll: Boolean = false
)

data class LongTermMemoryWritePlan(
    val patch: LongTermMemoryPatch,
    val reason: String,
    val confidence: Double
)

enum class WorkingMemoryStatus {
    NEW, IN_PROGRESS, BLOCKED, DONE
}

data class WorkingMemoryState(
    val taskId: String = "",
    val goal: String? = null,
    val constraints: List<String> = emptyList(),
    val decisions: List<String> = emptyList(),
    val openQuestions: List<String> = emptyList(),
    val nextSteps: List<String> = emptyList(),
    val artifacts: Map<String, String> = emptyMap(),
    val status: WorkingMemoryStatus = WorkingMemoryStatus.NEW,
    val updatedAt: Long = System.currentTimeMillis()
)

data class WorkingMemoryPatch(
    val setGoal: String? = null,
    val addConstraints: List<String> = emptyList(),
    val removeConstraints: List<String> = emptyList(),
    val addDecisions: List<String> = emptyList(),
    val removeDecisions: List<String> = emptyList(),
    val addOpenQuestions: List<String> = emptyList(),
    val closeOpenQuestions: List<String> = emptyList(),
    val addNextSteps: List<String> = emptyList(),
    val removeNextSteps: List<String> = emptyList(),
    val putArtifacts: Map<String, String> = emptyMap(),
    val removeArtifacts: List<String> = emptyList(),
    val setStatus: WorkingMemoryStatus? = null,
    val clearAll: Boolean = false
)

data class WorkingMemoryWritePlan(
    val patch: WorkingMemoryPatch,
    val reason: String,
    val confidence: Double
)

data class Profile(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val longTermMemory: LongTermMemory = LongTermMemory(),
    val invariants: List<String> = emptyList(),
    val tasks: List<TaskItem> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class TaskItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val workingMemory: WorkingMemoryState = WorkingMemoryState(taskId = id),
    val chats: List<Chat> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class Chat(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Новый чат",
    val rootChatId: String = id,
    val parentChatId: String? = null,
    val branchedFromMessageId: Long? = null,
    val settings: AppSettings = AppSettings(),
    val facts: String = "",
    val factsMessageCount: Int = 0,
    val messages: List<UiChatMessage> = emptyList(),
    val metrics: List<RunMetric> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class ChatMessageSource(
    val chunkId: String,
    val file: String,
    val section: String? = null
)

data class UiChatMessage(
    val id: Long = nextUiChatMessageId(),
    val role: Role,
    val text: String,
    val violatesInvariants: Boolean = false,
    val ragApplied: Boolean = false,
    val ragSources: List<ChatMessageSource> = emptyList(),
    val ragRewrittenQuery: String? = null,
    val ragTopKBefore: Int? = null,
    val ragTopKAfter: Int? = null,
    val ragSimilarityThreshold: Float? = null,
    val ragMode: String? = null
)

enum class Role { USER, ASSISTANT, TOOL }

private val uiChatMessageIdSeed = AtomicLong(System.currentTimeMillis() * 1_000L)

private fun nextUiChatMessageId(): Long = uiChatMessageIdSeed.incrementAndGet()
