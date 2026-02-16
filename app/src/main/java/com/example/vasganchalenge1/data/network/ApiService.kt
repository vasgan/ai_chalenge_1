package com.example.vasganchalenge1.data.network

import com.example.vasganchalenge1.data.ChatRequest
import com.example.vasganchalenge1.data.ChatResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("v1/chat/completions")
    suspend fun chatCompletion(
        @Body request: ChatRequest
    ): ChatResponse
}