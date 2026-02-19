package com.example.vasganchalenge1.data

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
    val choices: List<Choice>
)

data class Choice(
    val message: Message
)