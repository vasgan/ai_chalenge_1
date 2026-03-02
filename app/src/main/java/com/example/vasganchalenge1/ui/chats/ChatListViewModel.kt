package com.example.vasganchalenge1.ui.chats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vasganchalenge1.data.Chat
import com.example.vasganchalenge1.data.repositories.ChatStoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatListUiState(
    val taskId: String = "",
    val taskTitle: String = "",
    val chats: List<Chat> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val store: ChatStoreRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val taskId: String = checkNotNull(savedStateHandle["taskId"])

    private val _state = MutableStateFlow(ChatListUiState(taskId = taskId))
    val state = _state

    init {
        viewModelScope.launch {
            store.profilesFlow.collect { profiles ->
                val task = profiles.asSequence()
                    .flatMap { it.tasks.asSequence() }
                    .firstOrNull { it.id == taskId } ?: return@collect

                _state.value = _state.value.copy(
                    taskTitle = task.title,
                    chats = task.chats
                )
            }
        }
    }

    fun createChat(onDone: (String) -> Unit) {
        viewModelScope.launch {
            val chat = store.createChat(taskId = taskId, title = "Чат ${System.currentTimeMillis()}")
            onDone(chat.id)
        }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            store.deleteChat(chatId)
        }
    }
}
