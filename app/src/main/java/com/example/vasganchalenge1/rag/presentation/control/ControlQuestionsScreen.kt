package com.example.vasganchalenge1.rag.presentation.control

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.vasganchalenge1.rag.model.ControlQuestionsMode

@Composable
fun ControlQuestionsRoute(
    onBack: () -> Unit,
    viewModel: ControlQuestionsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    ControlQuestionsScreen(
        state = state,
        onBack = onBack,
        onQuestionChange = viewModel::onQuestionChange,
        onExpectationChange = viewModel::onExpectationChange,
        onExpectedSourcesChange = viewModel::onExpectedSourcesChange,
        onAddQuestion = viewModel::addQuestion,
        onDeleteQuestion = viewModel::deleteQuestion,
        onSave = viewModel::save,
        onGenerate = viewModel::generateQuestions,
        onClearError = viewModel::resetError
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlQuestionsScreen(
    state: ControlQuestionsUiState,
    onBack: () -> Unit,
    onQuestionChange: (String, String) -> Unit,
    onExpectationChange: (String, String) -> Unit,
    onExpectedSourcesChange: (String, String) -> Unit,
    onAddQuestion: () -> Unit,
    onDeleteQuestion: (String) -> Unit,
    onSave: () -> Unit,
    onGenerate: () -> Unit,
    onClearError: () -> Unit
) {
    val readOnly = state.mode == ControlQuestionsMode.READ_ONLY
    val questions = if (readOnly) state.persistedQuestions else state.draftQuestions

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (readOnly) {
                            "Контрольные вопросы (Read-only)"
                        } else {
                            "Контрольные вопросы"
                        }
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Назад") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Mode: ${state.mode}", style = MaterialTheme.typography.titleSmall)
                        Text("Index: ${state.indexId ?: "нет активного индекса"}")
                        if (!readOnly) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = onGenerate, enabled = !state.isGenerating) {
                                    Text(if (state.isGenerating) "Генерация..." else "Сгенерировать контрольные вопросы")
                                }
                                Button(onClick = onAddQuestion) {
                                    Text("Добавить")
                                }
                                Button(onClick = onSave, enabled = state.hasUnsavedChanges) {
                                    Text("Сохранить")
                                }
                            }
                        }
                        state.error?.let { errorText ->
                            Text(
                                text = errorText,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                            TextButton(onClick = onClearError) {
                                Text("Скрыть")
                            }
                        }
                    }
                }
            }

            if (questions.isEmpty()) {
                item {
                    Text(
                        text = if (state.indexId == null) {
                            "Сначала построй индекс на экране RAG"
                        } else {
                            "Контрольные вопросы отсутствуют"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            itemsIndexed(questions, key = { index, q -> "cq_${q.id}_$index" }) { _, question ->
                Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (readOnly) {
                            Text(question.question, style = MaterialTheme.typography.titleSmall)
                            Text("Expectation: ${question.expectation}")
                            Text("Sources: ${question.expectedSources.joinToString(", ")}")
                        } else {
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = question.question,
                                onValueChange = { onQuestionChange(question.id, it) },
                                label = { Text("Question") }
                            )
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = question.expectation,
                                onValueChange = { onExpectationChange(question.id, it) },
                                label = { Text("Expectation") }
                            )
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = question.expectedSources.joinToString("\n"),
                                onValueChange = { onExpectedSourcesChange(question.id, it) },
                                label = { Text("Expected sources (newline separated)") }
                            )
                            TextButton(onClick = { onDeleteQuestion(question.id) }) {
                                Text("Удалить")
                            }
                        }
                    }
                }
            }
        }
    }
}
