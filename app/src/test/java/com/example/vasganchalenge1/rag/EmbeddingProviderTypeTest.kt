package com.example.vasganchalenge1.rag

import com.example.vasganchalenge1.rag.model.EmbeddingProviderType
import org.junit.Assert.assertEquals
import org.junit.Test

class EmbeddingProviderTypeTest {

    @Test
    fun `fromRaw returns LOCAL for unknown value`() {
        assertEquals(EmbeddingProviderType.LOCAL, EmbeddingProviderType.fromRaw("UNKNOWN"))
        assertEquals(EmbeddingProviderType.LOCAL, EmbeddingProviderType.fromRaw(null))
    }

    @Test
    fun `fromRaw parses known values`() {
        assertEquals(EmbeddingProviderType.LOCAL, EmbeddingProviderType.fromRaw("LOCAL"))
        assertEquals(EmbeddingProviderType.OPENAI, EmbeddingProviderType.fromRaw("OPENAI"))
    }
}
