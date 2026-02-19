package com.example.vasganchalenge1.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onTemperatureChange: (String) -> Unit,
    onFormatChange: (String) -> Unit,
    onLengthLimitChange: (String) -> Unit,
    onStopChange: (String) -> Unit,
    onMaxTokensChange: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Режим запуска с условиями:")
                Switch(checked = state.enabled, onCheckedChange = onEnabledChange)
            }
            OutlinedTextField(
                value = state.temperature,
                onValueChange = onTemperatureChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Температура") },
                placeholder = { Text("Например: 0.7") },
                enabled = state.enabled
            )
            OutlinedTextField(
                value = state.format,
                onValueChange = onFormatChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Формат ответа:") },
                placeholder = { Text("Например: Ровно 3 пункта, без вступления.") },
                enabled = state.enabled
            )

            OutlinedTextField(
                value = state.lengthLimit,
                onValueChange = onLengthLimitChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ограничение длины (слова/символы):") },
                placeholder = { Text("Например: Не более 60 слов.") },
                enabled = state.enabled
            )

            OutlinedTextField(
                value = state.stopSequence,
                onValueChange = onStopChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Stop sequence (строка завершения):") },
                enabled = state.enabled
            )

            OutlinedTextField(
                value = state.maxTokensText,
                onValueChange = onMaxTokensChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("max_tokens (через API):") },
                singleLine = true,
                enabled = state.enabled
            )
        }
    }
}