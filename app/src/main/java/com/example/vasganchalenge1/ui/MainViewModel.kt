package com.example.vasganchalenge1.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vasganchalenge1.data.Role
import com.example.vasganchalenge1.data.UiChatMessage
import com.example.vasganchalenge1.data.repositories.AppSettings
import com.example.vasganchalenge1.data.repositories.EchoRepository
import com.example.vasganchalenge1.data.repositories.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repo: EchoRepository,
    settingsRepo: SettingsRepository
) : ViewModel() {

    private var _state = kotlinx.coroutines.flow.MutableStateFlow(MainUiState())
    val state = _state

    val settings = settingsRepo.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppSettings()
    )

    fun onInputChange(value: String) {
        _state.value = _state.value.copy(input = value, error = null)
    }

    fun onSendClick() {
        val text = _state.value.input.trim()
        val currentSettings = settings.value

        if (text.isEmpty()) {
            _state.value = _state.value.copy(error = "Введите текст")
            return
        }

        // 1) добавляем user-сообщение сразу
        val userMsg = UiChatMessage(role = Role.USER, text = text)

        _state.value = _state.value.copy(
            input = "",
            isLoading = true,
            error = null,
            messages = _state.value.messages + userMsg
        )

        viewModelScope.launch {
            val start = android.os.SystemClock.elapsedRealtime()

            runCatching {
                repo.send(text, currentSettings, _state.value.messages)
            }.onSuccess { result ->
                val latencyMs = android.os.SystemClock.elapsedRealtime() - start

                val tokensIn = result.tokensIn ?: 0
                val tokensOut = result.tokenOut ?: 0
                val total = tokensIn + tokensOut
                val cost = calcCostUsd(currentSettings.model, tokensIn, tokensOut)

                val metric = RunMetric(
                    model = currentSettings.model,
                    latencyMs = latencyMs,
                    totalTokens = total,
                    costUsd = cost
                )

                val assistantText = result.content.orEmpty()
                val assistantMsg = UiChatMessage(role = Role.ASSISTANT, text = assistantText)

                _state.value = _state.value.copy(
                    isLoading = false,
                    messages = _state.value.messages + assistantMsg,
                    metrics = listOf(metric) + _state.value.metrics
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Ошибка запроса"
                )
            }
        }
    }
}

data class RunMetric(
    val model: String,
    val latencyMs: Long,
    val totalTokens: Int,
    val costUsd: Double?
)
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