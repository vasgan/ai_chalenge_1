package com.example.vasganchalenge1.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vasganchalenge1.data.repositories.ChatStoreRepository
import com.example.vasganchalenge1.data.repositories.AppSettings
import com.example.vasganchalenge1.data.repositories.ContextMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


data class SettingsUiState(
    val enabled: Boolean = false,
    val contextMode: String = ContextMode.FACTS,
    val canEditContextMode: Boolean = true,
    val model: String = "gpt-4o-mini", // NEW
    val format: String = "",
    val lengthLimit: String = "",
    val stopSequence: String = "###END###",
    val maxTokensText: String = "200",
    val temperature: String = "0.7"
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: ChatStoreRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val chatId: String = checkNotNull(savedStateHandle["chatId"])
    val modelOptions = listOf("gpt-4.1-nano", "gpt-4.1-mini", "gpt-4.1") // NEW (можешь поменять)
    val contextModeOptions = listOf(ContextMode.FACTS, ContextMode.LAST_10)
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            store.chatsFlow.collect { chats ->
                val chat = chats.firstOrNull { it.id == chatId }
                val s = chat?.settings ?: AppSettings()
                _state.value = SettingsUiState(
                    enabled = s.enabled,
                    contextMode = s.contextMode,
                    canEditContextMode = chat?.messages?.isEmpty() != false,
                    model = s.model, // NEW
                    format = s.format,
                    lengthLimit = s.lengthLimit,
                    stopSequence = s.stopSequence,
                    maxTokensText = s.maxTokens.toString(),
                    temperature = s.temperature
                )
            }
        }
    }

    fun setModel(v: String) = _state.update { it.copy(model = v) }
    fun setEnabled(v: Boolean) = _state.update { it.copy(enabled = v) }
    fun setContextMode(v: String) = _state.update {
        if (!it.canEditContextMode) it else it.copy(contextMode = v)
    }
    fun setFormat(v: String) = _state.update { it.copy(format = v) }
    fun setLengthLimit(v: String) = _state.update { it.copy(lengthLimit = v) }
    fun setStopSequence(v: String) = _state.update { it.copy(stopSequence = v) }
    fun setMaxTokensText(v: String) = _state.update { it.copy(maxTokensText = v.filter { ch -> ch.isDigit() }) }
    fun setTemperature(v: String) = _state.update { it.copy(temperature = v) }
    fun save(onDone: () -> Unit) {
        val maxTokens = _state.value.maxTokensText.toIntOrNull() ?: 200
        viewModelScope.launch {
            val existingChat = store.getChat(chatId) ?: return@launch
            store.updateChat(
                existingChat.copy(
                    settings = AppSettings(
                        enabled = _state.value.enabled,
                        contextMode = existingChat.settings.contextMode.takeIf { !_state.value.canEditContextMode }
                            ?: _state.value.contextMode,
                        model = _state.value.model,
                        format = _state.value.format,
                        lengthLimit = _state.value.lengthLimit,
                        stopSequence = _state.value.stopSequence,
                        maxTokens = maxTokens,
                        temperature = _state.value.temperature
                    )
                )
            )
            onDone()
        }
    }
}
