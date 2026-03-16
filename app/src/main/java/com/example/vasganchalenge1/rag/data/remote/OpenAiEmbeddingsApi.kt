package com.example.vasganchalenge1.rag.data.remote

import retrofit2.http.Body
import retrofit2.http.POST

interface OpenAiEmbeddingsApi {
    @POST("v1/embeddings")
    suspend fun createEmbeddings(
        @Body request: OpenAIEmbeddingRequest
    ): OpenAIEmbeddingResponse
}
