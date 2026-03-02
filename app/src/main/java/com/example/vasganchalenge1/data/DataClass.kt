package com.example.vasganchalenge1.data

import com.example.vasganchalenge1.data.repositories.AppSettings
import java.util.UUID

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

data class LongTermMemory(
    val profileDescription: String = "",
    val communicationLanguage: String = "",
    val customFields: List<MemoryField> = emptyList()
)

data class WorkingMemory(
    val fields: List<MemoryField> = emptyList()
)

data class Profile(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val longTermMemory: LongTermMemory = LongTermMemory(),
    val tasks: List<TaskItem> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class TaskItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val workingMemory: WorkingMemory = WorkingMemory(),
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

data class UiChatMessage(
    val id: Long = System.currentTimeMillis(),
    val role: Role,
    val text: String
)

enum class Role { USER, ASSISTANT }
