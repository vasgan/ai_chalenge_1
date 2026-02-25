package com.example.vasganchalenge1.ui.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vasganchalenge1.data.Chat
import com.example.vasganchalenge1.data.repositories.ChatStoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatListUiState(
    val chats: List<Chat> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val store: ChatStoreRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ChatListUiState())
    val state = _state

    init {
        viewModelScope.launch {
            store.chatsFlow.collect { chats ->
                _state.value = _state.value.copy(chats = chats)
            }
        }
    }

    fun createChat() {
        viewModelScope.launch {
            store.createChat(title = "Чат ${System.currentTimeMillis()}")
        }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            store.deleteChat(chatId)
        }
    }
}