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
    suspend fun send(text: String, settings: AppSettings, history: List<UiChatMessage>): DataResponse {
        val messages = mutableListOf<Message>()
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
        val historyMessages = history
            .takeLast(10) // ⚠️ ограничиваем контекст!
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
}

data class DataResponse(val content: String?, val tokensIn: Int?, val tokenOut: Int?)