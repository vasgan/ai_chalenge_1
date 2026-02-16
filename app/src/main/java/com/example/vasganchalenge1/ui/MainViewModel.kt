package com.example.vasganchalenge1.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vasganchalenge1.data.repositories.EchoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repo: EchoRepository
) : ViewModel() {

    private var _state = kotlinx.coroutines.flow.MutableStateFlow(MainUiState())
    val state = _state

    fun onInputChange(value: String) {
        _state.value = _state.value.copy(input = value, error = null)
    }

    fun onSendClick() {
        val text = _state.value.input.trim()
        if (text.isEmpty()) {
            _state.value = _state.value.copy(error = "Введите текст")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            runCatching {
                repo.send(text)
            }.onSuccess { result ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    output = result
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