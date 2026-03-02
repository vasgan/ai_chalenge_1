package com.example.vasganchalenge1.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vasganchalenge1.data.LongTermMemory
import com.example.vasganchalenge1.data.MemoryField
import com.example.vasganchalenge1.data.Role
import com.example.vasganchalenge1.data.RunMetric
import com.example.vasganchalenge1.data.UiChatMessage
import com.example.vasganchalenge1.data.repositories.AppSettings
import com.example.vasganchalenge1.data.repositories.ChatStoreRepository
import com.example.vasganchalenge1.data.repositories.ContextMode
import com.example.vasganchalenge1.data.repositories.EchoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val FACTS_CHUNK_SIZE = 20

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repo: EchoRepository,
    private val store: ChatStoreRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val chatId: String = checkNotNull(savedStateHandle["chatId"])

    private val _state = MutableStateFlow(ChatUiState(chatId = chatId))
    val state = _state
    private val _settings = MutableStateFlow(AppSettings())
    val settings = _settings

    init {
        viewModelScope.launch {
            store.profilesFlow.collect { profiles ->
                val profile = profiles.firstOrNull { candidate ->
                    candidate.tasks.any { task -> task.chats.any { it.id == chatId } }
                } ?: return@collect
                val chat = profile.tasks.asSequence()
                    .flatMap { it.chats.asSequence() }
                    .firstOrNull { it.id == chatId } ?: return@collect
                _state.value = _state.value.copy(
                    profileId = profile.id,
                    profileTitle = profile.title,
                    rootChatId = chat.rootChatId,
                    parentChatId = chat.parentChatId,
                    branchedFromMessageId = chat.branchedFromMessageId,
                    title = chat.title,
                    facts = chat.facts,
                    profileDescription = profile.longTermMemory.profileDescription,
                    communicationLanguage = profile.longTermMemory.communicationLanguage,
                    longTermFields = profile.longTermMemory.customFields,
                    factsMessageCount = chat.factsMessageCount,
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

    fun createBranchFrom(messageId: Long, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val branch = store.createBranch(chatId, messageId)
            onDone(branch.id)
        }
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
            val currentFacts = _state.value.facts
            val currentFactsMessageCount = _state.value.factsMessageCount
            val preFactsResult = runCatching {
                updateFactsIfNeeded(
                    facts = currentFacts,
                    factsMessageCount = currentFactsMessageCount,
                    fullMessages = preMessagesRaw,
                    settings = currentSettings
                )
            }.getOrElse { e ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Ошибка обновления facts"
                )
                return@launch
            }
            val (preFacts, preFactsMessageCount) = preFactsResult

            if (preFacts != currentFacts || preFactsMessageCount != currentFactsMessageCount) {
                _state.value = _state.value.copy(
                    facts = preFacts,
                    factsMessageCount = preFactsMessageCount
                )
            }

            // сразу сохраним user-msg в чат (чтобы не потерялось при крэше)
            persistChat(
                facts = preFacts,
                factsMessageCount = preFactsMessageCount,
                messages = preMessagesRaw,
                metrics = _state.value.metrics
            )

            val start = android.os.SystemClock.elapsedRealtime()
            val requestHistory = buildRequestHistory(
                fullMessages = preMessagesRaw,
                settings = currentSettings
            )

            runCatching {
                repo.send(
                    settings = currentSettings,
                    history = requestHistory,
                    facts = if (currentSettings.contextMode == ContextMode.FACTS) preFacts else "",
                    longTermMemoryJson = buildLongTermMemoryJson(
                        LongTermMemory(
                            profileDescription = _state.value.profileDescription,
                            communicationLanguage = _state.value.communicationLanguage,
                            customFields = _state.value.longTermFields
                        )
                    )
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
                val updatedMessagesRaw = preMessagesRaw + assistantMsg
                val (updatedFacts, updatedFactsMessageCount) = runCatching {
                    updateFactsIfNeeded(
                        facts = preFacts,
                        factsMessageCount = preFactsMessageCount,
                        fullMessages = updatedMessagesRaw,
                        settings = currentSettings
                    )
                }.getOrElse {
                    // Не теряем ответ ассистента, даже если facts-запрос временно упал.
                    preFacts to preFactsMessageCount
                }
                val updatedMetrics = listOf(metric) + _state.value.metrics

                _state.value = _state.value.copy(
                    isLoading = false,
                    facts = updatedFacts,
                    factsMessageCount = updatedFactsMessageCount,
                    messages = updatedMessagesRaw,
                    metrics = updatedMetrics
                )

                persistChat(
                    facts = updatedFacts,
                    factsMessageCount = updatedFactsMessageCount,
                    messages = updatedMessagesRaw,
                    metrics = updatedMetrics
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Ошибка запроса"
                )
            }
        }
    }

    private suspend fun persistChat(
        facts: String,
        factsMessageCount: Int,
        messages: List<UiChatMessage>,
        metrics: List<RunMetric>
    ) {
        val existing = store.getChat(chatId) ?: return
        store.updateChat(
            existing.copy(
                facts = facts,
                factsMessageCount = factsMessageCount,
                messages = messages,
                metrics = metrics,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun updateFactsIfNeeded(
        facts: String,
        factsMessageCount: Int,
        fullMessages: List<UiChatMessage>,
        settings: AppSettings
    ): Pair<String, Int> {
        if (settings.contextMode == ContextMode.LAST_10) {
            return "" to 0
        }
        if (settings.contextMode != ContextMode.FACTS) return facts to factsMessageCount
        if (fullMessages.size <= 10) return facts to factsMessageCount

        val cutoffIndex = fullMessages.size - 10
        var updatedFacts = facts
        var coveredCount = factsMessageCount.coerceAtMost(cutoffIndex)

        while (coveredCount < cutoffIndex) {
            val nextCoveredCount = minOf(cutoffIndex, coveredCount + FACTS_CHUNK_SIZE)
            updatedFacts = repo.extractFacts(
                currentFacts = updatedFacts,
                chunk = fullMessages.subList(coveredCount, nextCoveredCount),
                settings = settings
            )
            coveredCount = nextCoveredCount
        }

        return updatedFacts to coveredCount
    }

    private fun buildRequestHistory(
        fullMessages: List<UiChatMessage>,
        settings: AppSettings
    ): List<UiChatMessage> {
        return when (settings.contextMode) {
            ContextMode.LAST_10 -> fullMessages.takeLast(10)
            ContextMode.FACTS -> fullMessages.takeLast(10)
            else -> fullMessages
        }
    }
}

private fun buildLongTermMemoryJson(longTermMemory: LongTermMemory): String {
    val entries = buildList {
        if (longTermMemory.profileDescription.isNotBlank()) {
            add(jsonEntry("profile_description", longTermMemory.profileDescription))
        }
        if (longTermMemory.communicationLanguage.isNotBlank()) {
            val language = longTermMemory.communicationLanguage
            add(
                jsonEntry(
                    "communication_language",
                    "You must respond ONLY in \"$language\".\n" +
                            "Do not switch language even if the user writes in another language.\n" +
                            "Do not include translations.\n" +
                            "If the user asks in another language, still answer in \"$language\"."
                )
            )
        }
        longTermMemory.customFields.forEach { field ->
            if (field.key.isNotBlank() && field.value.isNotBlank()) {
                add(jsonEntry(field.key, field.value))
            }
        }
    }
    return "{${entries.joinToString(",")}}"
}

private fun jsonEntry(key: String, value: String): String {
    return "\"${escapeJson(key)}\":\"${escapeJson(value)}\""
}

private fun escapeJson(value: String): String {
    return buildString(value.length) {
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
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
