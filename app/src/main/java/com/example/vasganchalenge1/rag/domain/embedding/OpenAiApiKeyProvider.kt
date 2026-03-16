package com.example.vasganchalenge1.rag.domain.embedding

import com.example.vasganchalenge1.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

interface OpenAiApiKeyProvider {
    fun getApiKeyOrNull(): String?
    fun isConfigured(): Boolean = !getApiKeyOrNull().isNullOrBlank()
}

@Singleton
class BuildConfigOpenAiApiKeyProvider @Inject constructor() : OpenAiApiKeyProvider {
    override fun getApiKeyOrNull(): String? {
        return BuildConfig.OPENAI_API_KEY.trim().ifBlank { null }
    }
}
