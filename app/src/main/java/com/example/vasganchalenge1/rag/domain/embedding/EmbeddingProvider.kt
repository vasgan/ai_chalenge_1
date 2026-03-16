package com.example.vasganchalenge1.rag.domain.embedding

import com.example.vasganchalenge1.rag.model.EmbeddingProviderType

interface EmbeddingProvider {
    val providerType: EmbeddingProviderType
    val modelName: String
    suspend fun embed(texts: List<String>): List<FloatArray>
}
