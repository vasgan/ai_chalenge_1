package com.example.vasganchalenge1.rag.domain.embedding

interface EmbeddingProvider {
    val engineName: String
    suspend fun embed(texts: List<String>): List<FloatArray>
}
