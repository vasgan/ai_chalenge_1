package com.example.vasganchalenge1.ui

import com.example.vasganchalenge1.data.RunMetric
import com.example.vasganchalenge1.data.UiChatMessage
import com.example.vasganchalenge1.data.LongTermMode
import com.example.vasganchalenge1.data.MemoryField
import com.example.vasganchalenge1.data.taskfsm.TaskState

data class ChatUiState(
    val chatId: String = "",
    val profileId: String = "",
    val profileTitle: String = "",
    val taskId: String = "",
    val taskTitle: String = "",
    val rootChatId: String = "",
    val parentChatId: String? = null,
    val branchedFromMessageId: Long? = null,
    val title: String = "",
    val facts: String = "",
    val longTermMode: LongTermMode = LongTermMode.MANUAL,
    val profileDescription: String = "",
    val communicationLanguage: String = "",
    val longTermFields: List<MemoryField> = emptyList(),
    val workingMemoryContext: String = "",
    val taskStateDebug: TaskState? = null,
    val factsMessageCount: Int = 0,
    val input: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val messages: List<UiChatMessage> = emptyList(),
    val metrics: List<RunMetric> = emptyList()
)
