package com.example.vasganchalenge1.rag.domain.embedding

import com.example.vasganchalenge1.di.LocalEmbeddingProvider
import com.example.vasganchalenge1.di.OpenAiEmbeddingProviderQualifier
import com.example.vasganchalenge1.rag.model.EmbeddingProviderType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmbeddingProviderSelector @Inject constructor(
    @LocalEmbeddingProvider private val localProvider: EmbeddingProvider,
    @OpenAiEmbeddingProviderQualifier private val openAIProvider: EmbeddingProvider,
    private val apiKeyProvider: OpenAiApiKeyProvider
) {
    fun get(type: EmbeddingProviderType): EmbeddingProvider {
        return when (type) {
            EmbeddingProviderType.LOCAL -> localProvider
            EmbeddingProviderType.OPENAI -> openAIProvider
        }
    }

    fun isOpenAiConfigured(): Boolean = apiKeyProvider.isConfigured()
}
