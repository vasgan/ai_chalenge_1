package com.example.vasganchalenge1.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vasganchalenge1.data.repositories.AppSettings
import com.example.vasganchalenge1.data.repositories.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


data class SettingsUiState(
    val enabled: Boolean = false,
    val format: String = "",
    val lengthLimit: String = "",
    val stopSequence: String = "###END###",
    val maxTokensText: String = "200"
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.settingsFlow.collect { s ->
                _state.value = SettingsUiState(
                    enabled = s.enabled,
                    format = s.format,
                    lengthLimit = s.lengthLimit,
                    stopSequence = s.stopSequence,
                    maxTokensText = s.maxTokens.toString()
                )
            }
        }
    }

    fun setEnabled(v: Boolean) = _state.update { it.copy(enabled = v) }
    fun setFormat(v: String) = _state.update { it.copy(format = v) }
    fun setLengthLimit(v: String) = _state.update { it.copy(lengthLimit = v) }
    fun setStopSequence(v: String) = _state.update { it.copy(stopSequence = v) }
    fun setMaxTokensText(v: String) = _state.update { it.copy(maxTokensText = v.filter { ch -> ch.isDigit() }) }

    fun save(onDone: () -> Unit) {
        val maxTokens = _state.value.maxTokensText.toIntOrNull() ?: 200
        viewModelScope.launch {
            repo.save(
                AppSettings(
                    enabled = _state.value.enabled,
                    format = _state.value.format,
                    lengthLimit = _state.value.lengthLimit,
                    stopSequence = _state.value.stopSequence,
                    maxTokens = maxTokens
                )
            )
            onDone()
        }
    }
}