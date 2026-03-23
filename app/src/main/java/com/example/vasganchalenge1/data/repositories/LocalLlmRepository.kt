package com.example.vasganchalenge1.data.repositories

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalLlmRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    moshi: Moshi
) : ChatRepository {

    private val requestAdapter = moshi.adapter(OllamaGenerateRequest::class.java)
    private val responseAdapter = moshi.adapter(OllamaGenerateResponse::class.java)

    override suspend fun sendMessage(message: String): String {
        return withContext(Dispatchers.IO) {
            val payload = OllamaGenerateRequest(
                model = OLLAMA_MODEL,
                prompt = message,
                stream = false
            )
            val body = requestAdapter.toJson(payload)
                .toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url(OLLAMA_URL)
                .post(body)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Local LLM HTTP ${response.code}")
                }
                val raw = response.body?.string().orEmpty()
                val parsed = responseAdapter.fromJson(raw)
                parsed?.response?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Local LLM returned empty response")
            }
        }
    }

    private companion object {
        const val OLLAMA_URL = "http://10.0.2.2:11434/api/generate"
        const val OLLAMA_MODEL = "phi3"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

@JsonClass(generateAdapter = true)
data class OllamaGenerateRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean
)

@JsonClass(generateAdapter = true)
data class OllamaGenerateResponse(
    val response: String? = null
)
