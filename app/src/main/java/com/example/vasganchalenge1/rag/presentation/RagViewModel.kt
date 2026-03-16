package com.example.vasganchalenge1.rag.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vasganchalenge1.rag.domain.RagIndexRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RagViewModel @Inject constructor(
    private val repository: RagIndexRepository
) : ViewModel() {

    private val _state = MutableStateFlow(RagUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.observeDocuments(),
                repository.observeLatestReport(),
                repository.observeExports()
            ) { documents, report, exports ->
                Triple(documents, report, exports)
            }.collect { (documents, report, exports) ->
                val current = _state.value
                _state.value = current.copy(
                    documents = documents,
                    latestReport = report,
                    exports = exports,
                    status = deriveStatus(
                        isImporting = current.isImporting,
                        isIndexing = current.isIndexing,
                        hasDocuments = documents.isNotEmpty(),
                        hasResult = current.latestIndexingResult != null,
                        hasError = current.error != null
                    )
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
        _state.value = _state.value.copy(
            isIndexing = true,
            error = null,
            status = RagScreenStatus.INDEXING
        )

        viewModelScope.launch {
            repository.buildIndex()
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
