package com.example.vasganchalenge1.data.repositories

import com.example.vasganchalenge1.data.ChatRequest
import com.example.vasganchalenge1.data.Message
import com.example.vasganchalenge1.data.Role
import com.example.vasganchalenge1.data.UiChatMessage
import com.example.vasganchalenge1.data.network.ApiService
import com.example.vasganchalenge1.data.network.EchoRequest
import javax.inject.Inject

class EchoRepository  @Inject constructor(
    private val api: ApiService
) {
    suspend fun send(
        text: String,
        settings: AppSettings,
        history: List<UiChatMessage>,
        summary: String
    ): DataResponse {
        val messages = mutableListOf<Message>()
        if (settings.summaryEnabled && summary.isNotBlank()) {
            messages += Message(
                "system",
                "Conversation summary (older messages):\n$summary"
            )
        }
        val mainMessage = if (settings.enabled) {
            listOf(
                Message(
                    "system",
                    "${settings.format}. ${settings.lengthLimit}."
                ),
                Message("user", text)
            )
        } else {
            listOf(Message("user", text))
        }
        val historyMessages = (if (settings.summaryEnabled) history.takeLast(10) else history)
            .map {
                Message(
                    role = if (it.role == Role.USER) "user" else "assistant",
                    content = it.text
                )
            }
        messages.addAll(historyMessages)
        messages.addAll(mainMessage)
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

    suspend fun summarizeMessages(
        currentSummary: String,
        chunk: List<UiChatMessage>,
        settings: AppSettings
    ): String {
        if (chunk.isEmpty()) return currentSummary

        val summarizeMessages = buildList {
            add(
                Message(
                    "system",
                    "You are a summarizer. Update the running summary of the conversation.\n" +
                            "Rules:\n" +
                            "- Keep key facts, decisions, preferences, constraints.\n" +
                            "- Keep open TODOs/questions.\n" +
                            "- Be concise (max 800 chars).\n" +
                            "- Return ONLY the updated summary text."
                )
            )
            add(Message("user", "Current summary:\n${currentSummary.ifBlank { "(empty)" }}"))
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
                messages = summarizeMessages,
                stop = null,
                max_tokens = null,
                temperature = null
            )
        )

        return response.choices.firstOrNull()?.message?.content?.trim().orEmpty().ifBlank { currentSummary }
    }
}

data class DataResponse(val content: String?, val tokensIn: Int?, val tokenOut: Int?)
