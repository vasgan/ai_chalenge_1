package com.example.vasganchalenge1.data.repositories

interface ChatRepository {
    suspend fun sendMessage(message: String): String
}

