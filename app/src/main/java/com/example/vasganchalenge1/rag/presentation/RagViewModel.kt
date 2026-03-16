package com.example.vasganchalenge1.rag.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vasganchalenge1.rag.data.settings.RagSettingsRepository
import com.example.vasganchalenge1.rag.domain.RagIndexRepository
import com.example.vasganchalenge1.rag.domain.embedding.EmbeddingProviderSelector
import com.example.vasganchalenge1.rag.model.EmbeddingProviderType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RagViewModel @Inject constructor(
    private val repository: RagIndexRepository,
    private val settingsRepository: RagSettingsRepository,
    private val embeddingProviderSelector: EmbeddingProviderSelector
) : ViewModel() {

    private val _state = MutableStateFlow(RagUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.observeDocuments(),
                repository.observeLatestReport(),
                repository.observeExports(),
                repository.observeLatestIndexingResult()
            ) { documents, report, exports, latestIndexingResult ->
                Quadruple(documents, report, exports, latestIndexingResult)
            }.collect { (documents, report, exports, latestIndexingResult) ->
                val current = _state.value
                _state.value = current.copy(
                    documents = documents,
                    latestReport = report,
                    exports = exports,
                    latestIndexingResult = latestIndexingResult,
                    status = deriveStatus(
                        isImporting = current.isImporting,
                        isIndexing = current.isIndexing,
                        hasDocuments = documents.isNotEmpty(),
                        hasResult = latestIndexingResult != null,
                        hasError = current.error != null
                    )
                )
            }
        }

        viewModelScope.launch {
            settingsRepository.selectedProviderFlow.collect { selected ->
                _state.value = _state.value.copy(
                    selectedEmbeddingProvider = selected,
                    openAiApiKeyConfigured = embeddingProviderSelector.isOpenAiConfigured()
                )
            }
        }
    }

    fun importDocuments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _state.value = _state.value.copy(
            isImporting = true,
            error = null,
            status = RagScreenStatus.IMPORTING
        )

        viewModelScope.launch {
            repository.importDocuments(uris)
                .onSuccess {
                    val current = _state.value
                    _state.value = current.copy(
                        isImporting = false,
                        error = null,
                        status = deriveStatus(
                            isImporting = false,
                            isIndexing = current.isIndexing,
                            hasDocuments = current.documents.isNotEmpty(),
                            hasResult = current.latestIndexingResult != null,
                            hasError = false
                        )
                    )
                }
                .onFailure { throwable ->
                    _state.value = _state.value.copy(
                        isImporting = false,
                        error = throwable.message ?: "Ошибка импорта файлов",
                        status = RagScreenStatus.ERROR
                    )
                }
        }
    }

    fun buildIndex() {
        val selectedProvider = _state.value.selectedEmbeddingProvider
        if (selectedProvider == EmbeddingProviderType.OPENAI &&
            !_state.value.openAiApiKeyConfigured
        ) {
            _state.value = _state.value.copy(
                error = "OpenAI API key не настроен. Добавьте OPENAI_API_KEY в gradle.properties",
                status = RagScreenStatus.ERROR
            )
            return
        }

        _state.value = _state.value.copy(
            isIndexing = true,
            error = null,
            status = RagScreenStatus.INDEXING
        )

        viewModelScope.launch {
            repository.buildIndex(selectedProvider)
                .onSuccess { result ->
                    _state.value = _state.value.copy(
                        isIndexing = false,
                        latestIndexingResult = result,
                        error = null,
                        status = RagScreenStatus.INDEXED
                    )
                }
                .onFailure { throwable ->
                    _state.value = _state.value.copy(
                        isIndexing = false,
                        error = throwable.message ?: "Ошибка индексации",
                        status = RagScreenStatus.ERROR
                    )
                }
        }
    }

    fun setEmbeddingProvider(type: EmbeddingProviderType) {
        viewModelScope.launch {
            settingsRepository.setSelectedProvider(type)
        }
    }

    fun deleteDocument(documentId: String) {
        _state.value = _state.value.copy(error = null)
        viewModelScope.launch {
            repository.deleteDocument(documentId)
                .onFailure { throwable ->
                    _state.value = _state.value.copy(
                        error = throwable.message ?: "Ошибка удаления файла",
                        status = RagScreenStatus.ERROR
                    )
                }
        }
    }

    fun deleteExport(exportId: String) {
        _state.value = _state.value.copy(error = null)
        viewModelScope.launch {
            repository.deleteExport(exportId)
                .onFailure { throwable ->
                    _state.value = _state.value.copy(
                        error = throwable.message ?: "Ошибка удаления экспортированного файла",
                        status = RagScreenStatus.ERROR
                    )
                }
        }
    }

    fun clearError() {
        val current = _state.value
        _state.value = current.copy(
            error = null,
            status = deriveStatus(
                isImporting = current.isImporting,
                isIndexing = current.isIndexing,
                hasDocuments = current.documents.isNotEmpty(),
                hasResult = current.latestIndexingResult != null,
                hasError = false
            )
        )
    }

    private fun deriveStatus(
        isImporting: Boolean,
        isIndexing: Boolean,
        hasDocuments: Boolean,
        hasResult: Boolean,
        hasError: Boolean
    ): RagScreenStatus {
        return when {
            hasError -> RagScreenStatus.ERROR
            isImporting -> RagScreenStatus.IMPORTING
            isIndexing -> RagScreenStatus.INDEXING
            hasResult -> RagScreenStatus.INDEXED
            hasDocuments -> RagScreenStatus.READY_TO_INDEX
            else -> RagScreenStatus.IDLE
        }
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
