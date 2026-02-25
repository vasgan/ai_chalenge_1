package com.example.vasganchalenge1.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vasganchalenge1.data.Role
import com.example.vasganchalenge1.data.RunMetric
import com.example.vasganchalenge1.data.UiChatMessage
import com.example.vasganchalenge1.data.repositories.AppSettings
import com.example.vasganchalenge1.data.repositories.ChatHistoryRepository
import com.example.vasganchalenge1.data.repositories.ChatStoreRepository
import com.example.vasganchalenge1.data.repositories.EchoRepository
import com.example.vasganchalenge1.data.repositories.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repo: EchoRepository,
    private val store: ChatStoreRepository,
    settingsRepo: SettingsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val chatId: String = checkNotNull(savedStateHandle["chatId"])

    private val _state = MutableStateFlow(ChatUiState(chatId = chatId))
    val state = _state

    val settings = settingsRepo.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppSettings()
    )

    init {
        viewModelScope.launch {
            store.chatsFlow.collect { chats ->
                val chat = chats.firstOrNull { it.id == chatId } ?: return@collect
                _state.value = _state.value.copy(
                    title = chat.title,
                    messages = chat.messages,
                    metrics = chat.metrics
                )
            }
        }
    }

    fun onInputChange(v: String) {
        _state.value = _state.value.copy(input = v, error = null)
    }

    fun onSendClick() {
        val text = _state.value.input.trim()
        if (text.isEmpty()) {
            _state.value = _state.value.copy(error = "Введите текст")
            return
        }

        val currentSettings = settings.value

        // добавляем user локально
        val userMsg = UiChatMessage(role = Role.USER, text = text)
        val preMessages = _state.value.messages + userMsg

        _state.value = _state.value.copy(
            input = "",
            isLoading = true,
            error = null,
            messages = preMessages
        )

        viewModelScope.launch {
            // сразу сохраним user-msg в чат (чтобы не потерялось при крэше)
            persistChat(messages = preMessages, metrics = _state.value.metrics)

            val start = android.os.SystemClock.elapsedRealtime()

            runCatching {
                repo.send(text, currentSettings, preMessages) // история уже с userMsg
            }.onSuccess { result ->
                val latencyMs = android.os.SystemClock.elapsedRealtime() - start
                val tokensIn = result.tokensIn ?: 0
                val tokensOut = result.tokenOut ?: 0
                val cost = calcCostUsd(currentSettings.model, tokensIn, tokensOut)
                val metric = RunMetric(
                    model = currentSettings.model,
                    latencyMs = latencyMs,
                    totalTokens = tokensIn + tokensOut,
                    totalUsageToken = if (_state.value.metrics.isNotEmpty()) _state.value.metrics.last().totalUsageToken else 0 + tokensOut + tokensIn,
                    costUsd = cost
                )

                val assistantMsg = UiChatMessage(role = Role.ASSISTANT, text = result.content.orEmpty())
                val updatedMessages = preMessages + assistantMsg
                val updatedMetrics = listOf(metric) + _state.value.metrics

                _state.value = _state.value.copy(
                    isLoading = false,
                    messages = updatedMessages,
                    metrics = updatedMetrics
                )

                persistChat(messages = updatedMessages, metrics = updatedMetrics)
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Ошибка запроса"
                )
            }
        }
    }

    private suspend fun persistChat(messages: List<UiChatMessage>, metrics: List<RunMetric>) {
        val existing = store.getChat(chatId) ?: return
        store.updateChat(
            existing.copy(
                messages = messages,
                metrics = metrics,
                updatedAt = System.currentTimeMillis()
            )
        )
    }
}

data class PricePer1M(val input: Double, val output: Double)

private val PRICES = mapOf(
    "gpt-4.1-nano" to PricePer1M(input = 0.15, output = 0.60),
    "gpt-4.1-mini" to PricePer1M(input = 0.40, output = 1.60),
    "gpt-4.1" to PricePer1M(input = 2.00, output = 8.00)
)

private fun calcCostUsd(model: String, promptTokens: Int, completionTokens: Int): Double {
    val p = PRICES[model] ?: return 0.0
    return (promptTokens / 1_000_000.0) * p.input +
            (completionTokens / 1_000_000.0) * p.output
}