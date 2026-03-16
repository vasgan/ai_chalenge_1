package com.example.vasganchalenge1.rag

import com.example.vasganchalenge1.rag.domain.embedding.EmbeddingProvider
import com.example.vasganchalenge1.rag.domain.embedding.EmbeddingProviderSelector
import com.example.vasganchalenge1.rag.domain.embedding.OpenAiApiKeyProvider
import com.example.vasganchalenge1.rag.model.EmbeddingProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class EmbeddingProviderSelectorTest {

    @Test
    fun `returns local provider for LOCAL`() {
        val local = FakeProvider(EmbeddingProviderType.LOCAL, "local-model")
        val remote = FakeProvider(EmbeddingProviderType.OPENAI, "text-embedding-3-small")
        val selector = EmbeddingProviderSelector(
            localProvider = local,
            openAIProvider = remote,
            apiKeyProvider = FakeApiKeyProvider("key")
        )

        assertSame(local, selector.get(EmbeddingProviderType.LOCAL))
        assertSame(remote, selector.get(EmbeddingProviderType.OPENAI))
    }

    @Test
    fun `openai configured status mirrors key provider`() {
        val selectorMissing = EmbeddingProviderSelector(
            localProvider = FakeProvider(EmbeddingProviderType.LOCAL, "local"),
            openAIProvider = FakeProvider(EmbeddingProviderType.OPENAI, "remote"),
            apiKeyProvider = FakeApiKeyProvider(null)
        )
        val selectorConfigured = EmbeddingProviderSelector(
            localProvider = FakeProvider(EmbeddingProviderType.LOCAL, "local"),
            openAIProvider = FakeProvider(EmbeddingProviderType.OPENAI, "remote"),
            apiKeyProvider = FakeApiKeyProvider("token")
        )

        assertEquals(false, selectorMissing.isOpenAiConfigured())
        assertEquals(true, selectorConfigured.isOpenAiConfigured())
    }

    private class FakeProvider(
        override val providerType: EmbeddingProviderType,
        override val modelName: String
    ) : EmbeddingProvider {
        override suspend fun embed(texts: List<String>): List<FloatArray> {
            return texts.map { floatArrayOf(it.length.toFloat()) }
        }
    }

    private class FakeApiKeyProvider(
        private val key: String?
    ) : OpenAiApiKeyProvider {
        override fun getApiKeyOrNull(): String? = key
    }
}
