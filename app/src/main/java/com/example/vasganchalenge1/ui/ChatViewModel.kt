package com.example.vasganchalenge1.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vasganchalenge1.data.Role
import com.example.vasganchalenge1.data.RunMetric
import com.example.vasganchalenge1.data.UiChatMessage
import com.example.vasganchalenge1.data.repositories.AppSettings
import com.example.vasganchalenge1.data.repositories.ChatStoreRepository
import com.example.vasganchalenge1.data.repositories.EchoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repo: EchoRepository,
    private val store: ChatStoreRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    // Сделай план по созданию Android приложения
    //можешь по этим критериям подобрать идею приложение нужно разработать
    //Давай разберем финансовое приложение
    //Можешь разбить его на 23 задачи
    //какие риски могут возникнуть при разработке этого приложения
    // придумай на каждый риск три способа решения риска

    private val chatId: String = checkNotNull(savedStateHandle["chatId"])

    private val _state = MutableStateFlow(ChatUiState(chatId = chatId))
    val state = _state
    private val _settings = MutableStateFlow(AppSettings())
    val settings = _settings

    init {
        viewModelScope.launch {
            store.chatsFlow.collect { chats ->
                val chat = chats.firstOrNull { it.id == chatId } ?: return@collect
                _state.value = _state.value.copy(
                    title = chat.title,
                    summary = chat.summary,
                    messages = chat.messages,
                    metrics = chat.metrics
                )
                _settings.value = chat.settings
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
        val preMessagesRaw = _state.value.messages + userMsg

        _state.value = _state.value.copy(
            input = "",
            isLoading = true,
            error = null,
            messages = preMessagesRaw
        )

        viewModelScope.launch {
            val currentSummary = _state.value.summary
            val preCompactResult = runCatching {
                compactHistoryIfNeeded(
                    summary = currentSummary,
                    messages = preMessagesRaw,
                    settings = currentSettings
                )
            }.getOrElse { e ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Ошибка обновления summary"
                )
                return@launch
            }
            val (preSummary, preMessages) = preCompactResult

            if (preSummary != currentSummary || preMessages !== preMessagesRaw) {
                _state.value = _state.value.copy(summary = preSummary, messages = preMessages)
            }

            // сразу сохраним user-msg в чат (чтобы не потерялось при крэше)
            persistChat(summary = preSummary, messages = preMessages, metrics = _state.value.metrics)

            val start = android.os.SystemClock.elapsedRealtime()

            runCatching {
                repo.send(
                    text = text,
                    settings = currentSettings,
                    history = preMessages,
                    summary = if (currentSettings.summaryEnabled) preSummary else ""
                ) // история уже с userMsg
            }.onSuccess { result ->
                val latencyMs = android.os.SystemClock.elapsedRealtime() - start
                val tokensIn = result.tokensIn ?: 0
                val tokensOut = result.tokenOut ?: 0
                val cost = calcCostUsd(currentSettings.model, tokensIn, tokensOut)
                val previousTotalUsageTokens = _state.value.metrics.firstOrNull()?.totalUsageToken ?: 0
                val metric = RunMetric(
                    model = currentSettings.model,
                    latencyMs = latencyMs,
                    totalTokens = tokensIn + tokensOut,
                    totalUsageToken = previousTotalUsageTokens + tokensIn + tokensOut,
                    costUsd = cost
                )

                val assistantMsg = UiChatMessage(role = Role.ASSISTANT, text = result.content.orEmpty())
                val updatedMessagesRaw = preMessages + assistantMsg
                val (updatedSummary, updatedMessages) = runCatching {
                    compactHistoryIfNeeded(
                        summary = preSummary,
                        messages = updatedMessagesRaw,
                        settings = currentSettings
                    )
                }.getOrElse {
                    // Не теряем ответ ассистента, даже если summary-запрос временно упал.
                    preSummary to updatedMessagesRaw
                }
                val updatedMetrics = listOf(metric) + _state.value.metrics

                _state.value = _state.value.copy(
                    isLoading = false,
                    summary = updatedSummary,
                    messages = updatedMessages,
                    metrics = updatedMetrics
                )

                persistChat(summary = updatedSummary, messages = updatedMessages, metrics = updatedMetrics)
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Ошибка запроса"
                )
            }
        }
    }

    private suspend fun persistChat(
        summary: String,
        messages: List<UiChatMessage>,
        metrics: List<RunMetric>
    ) {
        val existing = store.getChat(chatId) ?: return
        store.updateChat(
            existing.copy(
                summary = summary,
                messages = messages,
                metrics = metrics,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun compactHistoryIfNeeded(
        summary: String,
        messages: List<UiChatMessage>,
        settings: AppSettings
    ): Pair<String, List<UiChatMessage>> {
        if (!settings.summaryEnabled) return summary to messages
        if (messages.size <= 10) return summary to messages

        val chunkToSummarize = messages.dropLast(10)
        val keptMessages = messages.takeLast(10)
        val updatedSummary = repo.summarizeMessages(
            currentSummary = summary,
            chunk = chunkToSummarize,
            settings = settings
        )
        return updatedSummary to keptMessages
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
