package com.example.vasganchalenge1.ui.profiles

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
fun ProfileListScreen(
    state: ProfileListUiState,
    onOpenProfile: (String) -> Unit,
    onCreateProfile: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Новый профиль") },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название профиля") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = title.trim()
                        if (trimmed.isNotEmpty()) {
                            onCreateProfile(trimmed)
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
                Text("Профили", style = MaterialTheme.typography.titleLarge)
                Button(onClick = { showDialog = true }) { Text("Новый профиль") }
            }
        }
    ) { padding ->
        if (state.profiles.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Пока нет профилей. Создай новый профиль.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.profiles, key = { it.id }) { profile ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(profile.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Задач: ${profile.tasks.size}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            TextButton(onClick = { onOpenProfile(profile.id) }) {
                                Text("Открыть")
                            }
                        }
                    }
                }
            }
        }
    }
}
