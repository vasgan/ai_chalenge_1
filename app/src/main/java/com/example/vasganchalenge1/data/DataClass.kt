package com.example.vasganchalenge1.data

import com.squareup.moshi.JsonClass

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

data class UiChatMessage(
    val id: Long = System.currentTimeMillis(),
    val role: Role,
    val text: String
)

enum class Role { USER, ASSISTANT }