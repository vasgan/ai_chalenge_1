package com.example.vasganchalenge1.ui.branches

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.vasganchalenge1.data.Chat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranchesScreen(
    state: BranchesUiState,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ветки") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Назад") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.chats, key = { it.id }) { chat ->
                BranchCard(
                    chat = chat,
                    isCurrent = chat.id == state.currentChatId,
                    onOpenChat = onOpenChat
                )
            }
        }
    }
}

@Composable
private fun BranchCard(
    chat: Chat,
    isCurrent: Boolean,
    onOpenChat: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = chat.title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = buildString {
                    append(if (isCurrent) "Текущая ветка" else "Ветка")
                    chat.parentChatId?.let { append(" • parent: ").append(it.take(6)) }
                    chat.branchedFromMessageId?.let { append(" • checkpoint: ").append(it) }
                },
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Сообщений: ${chat.messages.size} • Facts: ${if (chat.facts.isBlank()) 0 else 1}",
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(
                onClick = { onOpenChat(chat.id) },
                enabled = !isCurrent
            ) {
                Text(if (isCurrent) "Открыта" else "Открыть")
            }
        }
    }
}
