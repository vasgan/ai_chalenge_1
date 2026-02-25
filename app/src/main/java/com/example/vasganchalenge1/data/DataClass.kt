package com.example.vasganchalenge1.data

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

data class Chat(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Новый чат",
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