package com.example.vasganchalenge1.ui.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsScreen(
    state: ProfileSettingsUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onProfileDescriptionChange: (String) -> Unit,
    onCommunicationLanguageChange: (String) -> Unit,
    onAddCustomField: () -> Unit,
    onCustomFieldKeyChange: (Long, String) -> Unit,
    onCustomFieldValueChange: (Long, String) -> Unit,
    onRemoveCustomField: (Long) -> Unit,
    onAddInvariant: () -> Unit,
    onInvariantChange: (Long, String) -> Unit,
    onRemoveInvariant: (Long) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки профиля") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Назад") }
                },
                actions = {
                    TextButton(onClick = onSave) { Text("Сохранить") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(state.profileTitle, style = MaterialTheme.typography.titleMedium)
            Text(
                "LongTerm память профиля",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = if (state.longTermMode.name == "AUTO") {
                    "Режим: автоматически составляемая"
                } else {
                    "Режим: ручное заполнение"
                },
                style = MaterialTheme.typography.bodyMedium
            )
            if (!state.isEditable) {
                Text(
                    text = "В этом профиле LongTerm обновляется автоматически из диалогов. Ручное редактирование отключено.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            OutlinedTextField(
                value = state.profileDescription,
                onValueChange = onProfileDescriptionChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Описание профиля") },
                enabled = state.isEditable
            )

            OutlinedTextField(
                value = state.communicationLanguage,
                onValueChange = onCommunicationLanguageChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Язык общения") },
                enabled = state.isEditable
            )

            Text("Дополнительные поля", style = MaterialTheme.typography.titleSmall)

            state.customFields.forEach { field ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = field.key,
                        onValueChange = { onCustomFieldKeyChange(field.id, it) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Ключ") },
                        enabled = state.isEditable
                    )
                    OutlinedTextField(
                        value = field.value,
                        onValueChange = { onCustomFieldValueChange(field.id, it) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Значение") },
                        enabled = state.isEditable
                    )
                    TextButton(
                        onClick = { onRemoveCustomField(field.id) },
                        enabled = state.isEditable
                    ) {
                        Text("Удалить")
                    }
                }
            }

            Button(onClick = onAddCustomField, enabled = state.isEditable) {
                Text("Добавить поле")
            }

            Text("Инварианты профиля", style = MaterialTheme.typography.titleSmall)
            Text(
                "Ассистент не должен предлагать решения, которые нарушают эти правила.",
                style = MaterialTheme.typography.bodySmall
            )

            state.invariants.forEach { invariant ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = invariant.value,
                        onValueChange = { onInvariantChange(invariant.id, it) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Инвариант") }
                    )
                    TextButton(onClick = { onRemoveInvariant(invariant.id) }) {
                        Text("Удалить")
                    }
                }
            }

            Button(onClick = onAddInvariant) {
                Text("Добавить инвариант")
            }
        }
    }
}
