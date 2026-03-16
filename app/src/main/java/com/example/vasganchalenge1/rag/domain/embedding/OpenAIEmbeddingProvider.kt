package com.example.vasganchalenge1.rag.domain.embedding

import com.example.vasganchalenge1.rag.data.remote.OpenAIEmbeddingRequest
import com.example.vasganchalenge1.rag.data.remote.OpenAiEmbeddingsApi
import com.example.vasganchalenge1.rag.model.EmbeddingProviderType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenAIEmbeddingProvider @Inject constructor(
    private val api: OpenAiEmbeddingsApi,
    private val apiKeyProvider: OpenAiApiKeyProvider
) : EmbeddingProvider {

    override val providerType: EmbeddingProviderType = EmbeddingProviderType.OPENAI
    override val modelName: String = OPENAI_EMBEDDING_MODEL

    override suspend fun embed(texts: List<String>): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()
        require(apiKeyProvider.isConfigured()) {
            "OpenAI API key не настроен. Добавьте OPENAI_API_KEY в gradle.properties/local env."
        }

        val result = ArrayList<FloatArray>(texts.size)
        texts.chunked(BATCH_SIZE).forEach { batch ->
            val response = api.createEmbeddings(
                OpenAIEmbeddingRequest(
                    model = OPENAI_EMBEDDING_MODEL,
                    input = batch,
                    encoding_format = "float"
                )
            )

            val sorted = response.data.sortedBy { it.index }
            require(sorted.size == batch.size) {
                "OpenAI embeddings mismatch: expected=${batch.size}, got=${sorted.size}"
            }

            sorted.forEachIndexed { index, item ->
                require(item.index == index) {
                    "OpenAI embeddings index mismatch at $index: got ${item.index}"
                }
                result += item.embedding.toFloatArray()
            }
        }

        require(result.size == texts.size) {
            "OpenAI embeddings total mismatch: expected=${texts.size}, got=${result.size}"
        }
        return result
    }

    companion object {
        const val OPENAI_EMBEDDING_MODEL = "text-embedding-3-small"
        private const val BATCH_SIZE = 64
    }
}
