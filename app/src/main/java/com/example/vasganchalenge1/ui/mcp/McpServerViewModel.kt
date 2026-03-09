package com.example.vasganchalenge1.ui.mcp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vasganchalenge1.data.repositories.McpRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface McpServerUiState {
    data object Idle : McpServerUiState
    data object Loading : McpServerUiState
    data class Success(val tools: List<String>) : McpServerUiState
    data class Error(val message: String) : McpServerUiState
}

@HiltViewModel
class McpServerViewModel @Inject constructor(
    private val repository: McpRepository
) : ViewModel() {
    private val _state = MutableStateFlow<McpServerUiState>(McpServerUiState.Idle)
    val state = _state.asStateFlow()

    fun connectAndLoadTools(serverUrl: String) {
        val normalized = serverUrl.trim()
        if (normalized.isBlank()) {
            _state.value = McpServerUiState.Error("Server URL is empty")
            return
        }

        viewModelScope.launch {
            _state.value = McpServerUiState.Loading
            repository.listTools(normalized)
                .onSuccess { tools ->
                    _state.value = McpServerUiState.Success(tools)
                }
                .onFailure { throwable ->
                    _state.value = McpServerUiState.Error(
                        throwable.message ?: "MCP connection error"
                    )
                }
        }
    }
}
