package com.example.vasganchalenge1.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MainRoute(
    vm: MainViewModel,
    onOpenSettings: () -> Unit
) {
    val state = vm.state.collectAsState().value

    MainScreen(
        state = state,
        onInputChange = vm::onInputChange,
        onSendClick = vm::onSendClick,
        onOpenSettings = onOpenSettings
    )
}

@Composable
fun MainScreen(
    state: MainUiState,
    onInputChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
    modifier = Modifier
    .fillMaxSize()
    .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Запрос на сервер",
                style = MaterialTheme.typography.titleLarge
            )
            TextButton(onClick = onOpenSettings) {
                Text("Настройки")
            }
        }
        OutlinedTextField(
            value = state.input,
            onValueChange = onInputChange,
            label = { Text("Введите текст") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Button(
            onClick = onSendClick,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Отправляю...")
            } else {
                Text("Send")
            }
        }

        state.error?.let { err ->
            Text(
                text = err,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Text(
            text = "Ответ:",
            style = MaterialTheme.typography.titleMedium
        )

        OutlinedTextField(
            value = state.output,
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp),
            readOnly = true
        )
    }
}