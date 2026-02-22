package com.example.vasganchalenge1.data.repositories

import com.example.vasganchalenge1.data.ChatRequest
import com.example.vasganchalenge1.data.Message
import com.example.vasganchalenge1.data.network.ApiService
import com.example.vasganchalenge1.data.network.EchoRequest
import javax.inject.Inject

class EchoRepository  @Inject constructor(
    private val api: ApiService
) {
    suspend fun send(text: String, settings: AppSettings): DataResponse {
        val response = if (settings.enabled)
         api.chatCompletion(
            ChatRequest(
                model = settings.model,
                messages = listOf(
                    Message("system", settings.format + ". " + settings.lengthLimit + ". "
                    + "At the very end of the response, you MUST write exactly this string: " + settings.stopSequence),
                    Message("user", text),
               ),
                stop = settings.stopSequence,
                max_tokens = settings.maxTokens,
                temperature = settings.temperature.toDouble()
            )
        )
        else api.chatCompletion(
            ChatRequest(
                model = "gpt-4o-mini",
                messages = listOf(
                    Message("user", text),
                ),
                stop = null,
                max_tokens = null,
                temperature = null
            )
        )
        return DataResponse(response.choices.firstOrNull()?.message?.content ,  response.usage?.prompt_tokens, response.usage?.completion_tokens)
    }
}

data class DataResponse(val content: String?, val tokensIn: Int?, val tokenOut: Int?)