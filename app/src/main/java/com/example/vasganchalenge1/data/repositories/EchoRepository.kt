package com.example.vasganchalenge1.data.repositories

import com.example.vasganchalenge1.data.ChatRequest
import com.example.vasganchalenge1.data.Message
import com.example.vasganchalenge1.data.Role
import com.example.vasganchalenge1.data.UiChatMessage
import com.example.vasganchalenge1.data.network.ApiService
import javax.inject.Inject

class EchoRepository  @Inject constructor(
    private val api: ApiService
) {
    suspend fun send(
        settings: AppSettings,
        history: List<UiChatMessage>,
        facts: String
    ): DataResponse {
        val messages = mutableListOf<Message>()
        if (settings.contextMode == ContextMode.FACTS && facts.isNotBlank()) {
            messages += Message(
                "system",
                "Conversation facts from older messages:\n$facts"
            )
        }
        val systemMessages = if (settings.enabled) {
            listOf(
                Message(
                    "system",
                    "${settings.format}. ${settings.lengthLimit}."
                )
            )
        } else {
            emptyList()
        }
        val historyMessages = history.map {
            Message(
                role = if (it.role == Role.USER) "user" else "assistant",
                content = it.text
            )
        }
        messages.addAll(historyMessages)
        messages.addAll(systemMessages)
        val response = api.chatCompletion(
            ChatRequest(
                model = settings.model,
                messages = messages,
                stop = if (settings.enabled) settings.stopSequence else null,
                max_tokens = if (settings.enabled) settings.maxTokens else null,
                temperature = if (settings.enabled) settings.temperature.toDouble() else null
            )
        )
        return DataResponse(
            content = response.choices.firstOrNull()?.message?.content,
            tokensIn = response.usage?.prompt_tokens,
            tokenOut = response.usage?.completion_tokens
        )
    }

    suspend fun extractFacts(
        currentFacts: String,
        chunk: List<UiChatMessage>,
        settings: AppSettings
    ): String {
        if (chunk.isEmpty()) return currentFacts

        val factsMessages = buildList {
            add(
                Message(
                    "system",
                    "You extract and update durable facts from a conversation.\n" +
                            "Rules:\n" +
                            "- Keep only durable facts, decisions, preferences, and constraints.\n" +
                            //"- Keep open TODOs/questions.\n" +
                            "- Be concise (max 800 chars).\n" +
                            "- Avoid transient chatter.\n" +
                            "- Return ONLY the updated facts text."
                )
            )
            add(Message("user", "Current facts:\n${currentFacts.ifBlank { "(empty)" }}"))
            add(
                Message(
                    "user",
                    "New messages to incorporate:\n" +
                            chunk.joinToString("\n") { "${it.role}: ${it.text}" }
                )
            )
        }

        val response = api.chatCompletion(
            ChatRequest(
                model = settings.model,
                messages = factsMessages,
                stop = null,
                max_tokens = null,
                temperature = null
            )
        )

        return response.choices.firstOrNull()?.message?.content?.trim().orEmpty().ifBlank { currentFacts }
    }
}

data class DataResponse(val content: String?, val tokensIn: Int?, val tokenOut: Int?)
