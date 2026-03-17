package com.example.vasganchalenge1.rag.presentation.control

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vasganchalenge1.rag.domain.control.ControlQuestionsGenerator
import com.example.vasganchalenge1.rag.domain.control.ControlQuestionsRepository
import com.example.vasganchalenge1.rag.model.ControlQuestion
import com.example.vasganchalenge1.rag.model.ControlQuestionsMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ControlQuestionsViewModel @Inject constructor(
    private val repository: ControlQuestionsRepository,
    private val generator: ControlQuestionsGenerator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val mode = ControlQuestionsMode.fromRoute(savedStateHandle["mode"])
    private val autoGenerate = savedStateHandle.get<Boolean>("autoGenerate") ?: false

    private val _state = MutableStateFlow(
        ControlQuestionsUiState(mode = mode)
    )
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeLatestIndexId().collect { latestIndexId ->
                val current = _state.value
                _state.value = current.copy(indexId = latestIndexId)
            }
        }

        viewModelScope.launch {
            repository.observeQuestionsForLatestIndex().collect { persistedQuestions ->
                val current = _state.value
                _state.value = current.copy(
                    persistedQuestions = persistedQuestions,
                    draftQuestions = if (current.hasUnsavedChanges) current.draftQuestions else persistedQuestions,
                    isLoading = false
                )
            }
        }

        if (autoGenerate && mode == ControlQuestionsMode.EDITABLE) {
            generateQuestions()
        }
    }

    fun onQuestionChange(questionId: String, value: String) {
        if (_state.value.mode == ControlQuestionsMode.READ_ONLY) return
        updateDraft(questionId) { it.copy(question = value) }
    }

    fun onExpectationChange(questionId: String, value: String) {
        if (_state.value.mode == ControlQuestionsMode.READ_ONLY) return
        updateDraft(questionId) { it.copy(expectation = value) }
    }

    fun onExpectedSourcesChange(questionId: String, raw: String) {
        if (_state.value.mode == ControlQuestionsMode.READ_ONLY) return
        val parsed = raw.split('\n', ',', ';')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        updateDraft(questionId) { it.copy(expectedSources = parsed) }
    }

    fun addQuestion() {
        if (_state.value.mode == ControlQuestionsMode.READ_ONLY) return
        val indexId = _state.value.indexId ?: return
        val current = _state.value
        _state.value = current.copy(
            draftQuestions = current.draftQuestions + ControlQuestion(
                id = UUID.randomUUID().toString(),
                indexId = indexId,
                question = "",
                expectation = "",
                expectedSources = emptyList()
            ),
            hasUnsavedChanges = true
        )
    }

    fun deleteQuestion(questionId: String) {
        if (_state.value.mode == ControlQuestionsMode.READ_ONLY) return
        val current = _state.value
        _state.value = current.copy(
            draftQuestions = current.draftQuestions.filterNot { it.id == questionId },
            hasUnsavedChanges = true
        )
    }

    fun save() {
        if (_state.value.mode == ControlQuestionsMode.READ_ONLY) return
        val indexId = _state.value.indexId
        if (indexId.isNullOrBlank()) {
            _state.value = _state.value.copy(error = "Нет индекса для сохранения")
            return
        }

        viewModelScope.launch {
            runCatching {
                repository.replaceQuestions(indexId, _state.value.draftQuestions)
            }.onSuccess {
                _state.value = _state.value.copy(
                    hasUnsavedChanges = false,
                    error = null
                )
            }.onFailure { throwable ->
                _state.value = _state.value.copy(
                    error = throwable.message ?: "Ошибка сохранения контрольных вопросов"
                )
            }
        }
    }

    fun generateQuestions() {
        if (_state.value.mode == ControlQuestionsMode.READ_ONLY) return

        viewModelScope.launch {
            _state.value = _state.value.copy(isGenerating = true, error = null)
            val summary = repository.buildKnowledgeSummary().getOrElse { throwable ->
                _state.value = _state.value.copy(
                    isGenerating = false,
                    error = throwable.message ?: "Нет данных индекса для генерации"
                )
                return@launch
            }

            val generated = generator.generate(summary).getOrElse { throwable ->
                _state.value = _state.value.copy(
                    isGenerating = false,
                    error = throwable.message ?: "Ошибка генерации вопросов"
                )
                return@launch
            }

            val generatedQuestions = generated.map { item ->
                ControlQuestion(
                    id = UUID.randomUUID().toString(),
                    indexId = summary.indexId,
                    question = item.question,
                    expectation = item.expectation,
                    expectedSources = item.expectedSources
                )
            }

            runCatching {
                repository.replaceQuestions(summary.indexId, generatedQuestions)
            }.onSuccess {
                _state.value = _state.value.copy(
                    isGenerating = false,
                    hasUnsavedChanges = false,
                    draftQuestions = generatedQuestions,
                    error = null
                )
            }.onFailure { throwable ->
                _state.value = _state.value.copy(
                    isGenerating = false,
                    error = throwable.message ?: "Ошибка сохранения сгенерированных вопросов"
                )
            }
        }
    }

    fun resetError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun updateDraft(questionId: String, transform: (ControlQuestion) -> ControlQuestion) {
        val current = _state.value
        _state.value = current.copy(
            draftQuestions = current.draftQuestions.map { question ->
                if (question.id == questionId) transform(question) else question
            },
            hasUnsavedChanges = true
        )
    }
}

data class ControlQuestionsUiState(
    val mode: ControlQuestionsMode,
    val indexId: String? = null,
    val persistedQuestions: List<ControlQuestion> = emptyList(),
    val draftQuestions: List<ControlQuestion> = emptyList(),
    val isLoading: Boolean = true,
    val isGenerating: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val error: String? = null
)
