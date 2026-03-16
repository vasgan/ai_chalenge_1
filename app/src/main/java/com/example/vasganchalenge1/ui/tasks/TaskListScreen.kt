package com.example.vasganchalenge1.ui.tasks

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TaskListScreen(
    state: TaskListUiState,
    onBack: () -> Unit,
    onOpenProfileSettings: () -> Unit,
    onOpenRag: () -> Unit,
    onOpenTask: (String) -> Unit,
    onCreateTask: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Новая задача") },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название задачи") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = title.trim()
                        if (trimmed.isNotEmpty()) {
                            onCreateTask(trimmed)
                            title = ""
                            showDialog = false
                        }
                    }
                ) { Text("Создать") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Отмена") }
            }
        )
    }

    Scaffold(
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Задачи", style = MaterialTheme.typography.titleLarge)
                    Text(state.profileTitle, style = MaterialTheme.typography.bodySmall)
                }
                Row {
                    TextButton(onClick = onOpenRag) { Text("RAG") }
                    TextButton(onClick = onOpenProfileSettings) { Text("Профиль") }
                    TextButton(onClick = onBack) { Text("Назад") }
                    Button(onClick = { showDialog = true }) { Text("Новая задача") }
                }
            }
        }
    ) { padding ->
        if (state.tasks.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Пока нет задач. Создай новую задачу.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.tasks, key = { it.id }) { task ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(task.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Чатов: ${task.chats.size}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            TextButton(onClick = { onOpenTask(task.id) }) {
                                Text("Открыть")
                            }
                        }
                    }
                }
            }
        }
    }
}
