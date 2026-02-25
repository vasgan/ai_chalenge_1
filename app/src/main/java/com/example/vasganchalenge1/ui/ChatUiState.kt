package com.example.vasganchalenge1.ui

import com.example.vasganchalenge1.data.RunMetric
import com.example.vasganchalenge1.data.UiChatMessage

data class ChatUiState(
    val chatId: String = "",
    val title: String = "",
    val input: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val messages: List<UiChatMessage> = emptyList(),
    val metrics: List<RunMetric> = emptyList()
)