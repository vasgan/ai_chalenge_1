package com.example.vasganchalenge1.data.repositories

import com.example.vasganchalenge1.data.ChatRequest
import com.example.vasganchalenge1.data.Message
import com.example.vasganchalenge1.data.network.ApiService
import com.example.vasganchalenge1.data.network.EchoRequest
import javax.inject.Inject

class EchoRepository  @Inject constructor(
    private val api: ApiService
) {
    suspend fun send(text: String): String {
        val response = api.chatCompletion(
            ChatRequest(
                model = "gpt-4o-mini",
                messages = listOf(
                    Message("user", text)
                )
            )
        )
        return response.choices.firstOrNull()?.message?.content ?: "No response"
    }
}