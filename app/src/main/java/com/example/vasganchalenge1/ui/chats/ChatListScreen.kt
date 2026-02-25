package com.example.vasganchalenge1.ui.chats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ChatListScreen(
    state: ChatListUiState,
    onOpenChat: (String) -> Unit,
    onCreateChat: () -> Unit,
    onDeleteChat: (String) -> Unit
) {
    Scaffold(
        topBar = {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Чаты", style = MaterialTheme.typography.titleLarge)
                Button(onClick = onCreateChat) { Text("Новый чат") }
            }
        }
    ) { padding ->
        if (state.chats.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Пока нет чатов. Нажми «Новый чат».")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.chats, key = { it.id }) { chat ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(chat.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Сообщений: ${chat.messages.size} • Запусков: ${chat.metrics.size}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Row {
                                TextButton(onClick = { onOpenChat(chat.id) }) { Text("Открыть") }
                                TextButton(onClick = { onDeleteChat(chat.id) }) { Text("Удалить") }
                            }
                        }
                    }
                }
            }
        }
    }
}