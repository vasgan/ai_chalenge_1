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
    onRemoveCustomField: (Long) -> Unit
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

            OutlinedTextField(
                value = state.profileDescription,
                onValueChange = onProfileDescriptionChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Описание профиля") }
            )

            OutlinedTextField(
                value = state.communicationLanguage,
                onValueChange = onCommunicationLanguageChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Язык общения") }
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
                        label = { Text("Ключ") }
                    )
                    OutlinedTextField(
                        value = field.value,
                        onValueChange = { onCustomFieldValueChange(field.id, it) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Значение") }
                    )
                    TextButton(onClick = { onRemoveCustomField(field.id) }) {
                        Text("Удалить")
                    }
                }
            }

            Button(onClick = onAddCustomField) {
                Text("Добавить поле")
            }
        }
    }
}
