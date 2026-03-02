package com.example.vasganchalenge1.ui.branches

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vasganchalenge1.data.Chat
import com.example.vasganchalenge1.data.repositories.ChatStoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BranchesUiState(
    val currentChatId: String = "",
    val currentRootChatId: String = "",
    val chats: List<Chat> = emptyList()
)

@HiltViewModel
class BranchesViewModel @Inject constructor(
    private val store: ChatStoreRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val chatId: String = checkNotNull(savedStateHandle["chatId"])

    private val _state = MutableStateFlow(BranchesUiState(currentChatId = chatId))
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            store.chatsFlow.collect { chats ->
                val currentChat = chats.firstOrNull { it.id == chatId } ?: return@collect
                val relatedChats = chats
                    .filter { it.rootChatId == currentChat.rootChatId }
                    .sortedByDescending { it.updatedAt }

                _state.value = BranchesUiState(
                    currentChatId = chatId,
                    currentRootChatId = currentChat.rootChatId,
                    chats = relatedChats
                )
            }
        }
    }
}
