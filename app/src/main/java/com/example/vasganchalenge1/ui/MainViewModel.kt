package com.example.vasganchalenge1.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
        val settings = settings.value
        if (text.isEmpty()) {
            _state.value = _state.value.copy(error = "Введите текст")
            return
        }
        val start = android.os.SystemClock.elapsedRealtime()
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            runCatching {
                repo.send(text, settings)
            }.onSuccess { result ->
                val latencyMs = android.os.SystemClock.elapsedRealtime() - start
                val cost = result.tokensIn?.let { result.tokenOut?.let { completionTokens -> calcCostUsd(settings.model, it, completionTokens) } }
                val metric = result.tokenOut?.let {
                    result.tokensIn?.let { it1 ->
                        RunMetric(
                            model = settings.model,
                            latencyMs = latencyMs,
                            totalTokens = it1 + it,
                            costUsd = cost
                        )
                    }
                }
                _state.value = _state.value.copy(
                    isLoading = false,
                    output = result,
                    time = latencyMs,
                    metrics = (listOf(metric) + _state.value.metrics) as List<RunMetric> // добавляем сверху
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