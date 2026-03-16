package com.example.vasganchalenge1.rag.data.remote

data class OpenAIEmbeddingRequest(
    val model: String,
    val input: List<String>,
    val encoding_format: String = "float"
)

data class OpenAIEmbeddingResponse(
    val data: List<OpenAIEmbeddingItem>,
    val model: String
)

data class OpenAIEmbeddingItem(
    val embedding: List<Float>,
    val index: Int
)
