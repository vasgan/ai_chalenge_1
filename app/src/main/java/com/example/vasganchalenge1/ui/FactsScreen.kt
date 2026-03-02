package com.example.vasganchalenge1.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.example.vasganchalenge1.data.MemoryField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FactsScreen(
    profileDescription: String,
    communicationLanguage: String,
    longTermFields: List<MemoryField>,
    workingMemoryContext: String,
    totalUsageTokens: Int,
    userMessagesCount: Int,
    assistantMessagesCount: Int,
    totalMessagesCount: Int,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Facts") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Назад") }
                }
            )
        }
    ) { padding ->
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Общее количество токенов: $totalUsageTokens",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Сообщения (в чате): всего $totalMessagesCount • user $userMessagesCount • assistant $assistantMessagesCount",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium
            )
            MemoryCard(title = "Описание профиля", value = profileDescription)
            MemoryCard(title = "Язык общения", value = communicationLanguage)
            longTermFields.forEach { field ->
                MemoryCard(title = field.key, value = field.value)
            }
            MemoryCard(title = "Working Memory", value = workingMemoryContext)
        }
    }
}

@Composable
private fun MemoryCard(title: String, value: String) {
    Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 1.dp) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(
                text = value.ifBlank { "Не заполнено" },
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
