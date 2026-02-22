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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
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
    val scrollState = rememberScrollState()

    Column(
    modifier = Modifier
    .fillMaxSize()
        .verticalScroll(scrollState)
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
            maxLines = Int.MAX_VALUE
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
            value = state.output.content.toString(),
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp),
            readOnly = true
        )

        MetricsTable(
            metrics = state.metrics,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

@Composable
fun MetricsTable(metrics: List<RunMetric>, modifier: Modifier = Modifier) {
    val fmt = remember { java.text.DecimalFormat("0.000000") }

    Column(modifier = modifier.fillMaxWidth()) {

        // Header
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text("Model", Modifier.weight(1.4f))
            Text("Time", Modifier.weight(0.7f))
            Text("Tokens", Modifier.weight(0.8f))
            Text("Price", Modifier.weight(0.8f))
        }
        Divider()

        if (metrics.isEmpty()) {
            Text("Пока нет запусков", Modifier.padding(vertical = 10.dp))
            return
        }

        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp) // чтобы не съедало весь экран
        ) {
            items(metrics.size) { i ->
                val m = metrics[i]
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(m.model, Modifier.weight(1.4f))
                    Text("${m.latencyMs}ms", Modifier.weight(0.7f))
                    Text("${m.totalTokens}", Modifier.weight(0.8f))
                    Text("$${fmt.format(m.costUsd)}", Modifier.weight(0.8f))
                }
                Divider()
            }
        }
    }
}