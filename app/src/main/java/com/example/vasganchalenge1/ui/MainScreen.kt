package com.example.vasganchalenge1.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.vasganchalenge1.data.Role
import com.example.vasganchalenge1.data.UiChatMessage

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
    val listState = rememberLazyListState()

    // автоскролл вниз, когда добавились сообщения
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Агент", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onOpenSettings) { Text("Настройки") }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = state.input,
                        onValueChange = onInputChange,
                        modifier = Modifier.weight(1f),
                        label = { Text("Сообщение") },
                        maxLines = 6
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onSendClick,
                        enabled = !state.isLoading
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Text("Send")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // метрики (последняя строка)
            MetricsHeader(metrics = state.metrics)

            Divider()

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.messages, key = { it.id }) { msg ->
                    ChatBubble(msg)
                }

                // чтобы низ не прилипал к bottomBar
                item { Spacer(Modifier.height(60.dp)) }
            }
        }
    }
}


@Composable
private fun MetricsHeader(metrics: List<RunMetric>) {
    val last = metrics.firstOrNull()
    if (last == null) {
        Text(
            text = "Метрики: —",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        return
    }

    val priceText = last.costUsd?.let { "$" + String.format("%.6f", it) } ?: "—"

    Text(
        text = "Model: ${last.model} • Time: ${last.latencyMs}ms • Tokens: ${last.totalTokens} • Price: $priceText",
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun ChatBubble(msg: UiChatMessage) {
    val isUser = msg.role == Role.USER

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 2.dp,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (isUser) "You" else "Assistant",
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(msg.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
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