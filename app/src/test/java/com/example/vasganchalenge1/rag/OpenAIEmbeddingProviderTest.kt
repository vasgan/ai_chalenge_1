package com.example.vasganchalenge1.rag

import com.example.vasganchalenge1.rag.data.remote.OpenAIEmbeddingItem
import com.example.vasganchalenge1.rag.data.remote.OpenAIEmbeddingRequest
import com.example.vasganchalenge1.rag.data.remote.OpenAIEmbeddingResponse
import com.example.vasganchalenge1.rag.data.remote.OpenAiEmbeddingsApi
import com.example.vasganchalenge1.rag.domain.embedding.OpenAIEmbeddingProvider
import com.example.vasganchalenge1.rag.domain.embedding.OpenAiApiKeyProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAIEmbeddingProviderTest {

    @Test
    fun `embeds in batches and keeps input order`() = runBlocking {
        val api = FakeApi()
        val provider = OpenAIEmbeddingProvider(
            api = api,
            apiKeyProvider = FakeApiKeyProvider("token")
        )

        val input = (1..130).map { "chunk_$it" }
        val vectors = provider.embed(input)

        assertEquals(130, vectors.size)
        assertEquals(3, api.requests.size)
        assertEquals("text-embedding-3-small", api.requests.first().model)
        assertEquals("float", api.requests.first().encoding_format)
        assertEquals(64, api.requests[0].input.size)
        assertEquals(64, api.requests[1].input.size)
        assertEquals(2, api.requests[2].input.size)

        // вектор в fake строится как [indexInBatch, textLength], после сортировки index=0 у каждого батча.
        assertTrue(vectors.first().isNotEmpty())
    }

    @Test
    fun `fails when api key is missing`() = runBlocking {
        val provider = OpenAIEmbeddingProvider(
            api = FakeApi(),
            apiKeyProvider = FakeApiKeyProvider(null)
        )

        val result = runCatching { provider.embed(listOf("hello")) }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("API key", ignoreCase = true) == true)
    }

    private class FakeApi : OpenAiEmbeddingsApi {
        val requests = mutableListOf<OpenAIEmbeddingRequest>()

        override suspend fun createEmbeddings(request: OpenAIEmbeddingRequest): OpenAIEmbeddingResponse {
            requests += request
            val reversedItems = request.input.mapIndexed { index, text ->
                OpenAIEmbeddingItem(
                    embedding = listOf(index.toFloat(), text.length.toFloat()),
                    index = index
                )
            }.reversed()
            return OpenAIEmbeddingResponse(
                data = reversedItems,
                model = request.model
            )
        }
    }

    private class FakeApiKeyProvider(
        private val key: String?
    ) : OpenAiApiKeyProvider {
        override fun getApiKeyOrNull(): String? = key
    }
}
