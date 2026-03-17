package com.example.vasganchalenge1.rag.model

data class RetrievedChunk(
    val chunkId: String,
    val score: Float,
    val text: String,
    val source: String,
    val file: String,
    val section: String?
)

data class RagAnswerSource(
    val chunkId: String,
    val file: String,
    val section: String?
)
