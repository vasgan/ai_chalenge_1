package com.example.vasganchalenge1.rag.presentation

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.vasganchalenge1.rag.model.EmbeddingProviderType

@Composable
fun RagRoute(
    onBack: () -> Unit,
    viewModel: RagViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    RagScreen(
        state = state,
        onBack = onBack,
        onImport = viewModel::importDocuments,
        onBuildIndex = viewModel::buildIndex,
        onClearError = viewModel::clearError,
        onDeleteDocument = viewModel::deleteDocument,
        onDeleteExport = viewModel::deleteExport,
        onSelectEmbeddingProvider = viewModel::setEmbeddingProvider
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RagScreen(
    state: RagUiState,
    onBack: () -> Unit,
    onImport: (List<Uri>) -> Unit,
    onBuildIndex: () -> Unit,
    onClearError: () -> Unit,
    onDeleteDocument: (String) -> Unit,
    onDeleteExport: (String) -> Unit,
    onSelectEmbeddingProvider: (EmbeddingProviderType) -> Unit
) {
    val context = LocalContext.current

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult

        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        onImport(uris)
    }

    val formattedStatus = remember(state.status) { state.status.name }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RAG") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Назад")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 1.dp) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Статус: $formattedStatus", style = MaterialTheme.typography.titleSmall)
                        Text("Embedding provider", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProviderOption(
                                title = "Local (Google AI Edge)",
                                selected = state.selectedEmbeddingProvider == EmbeddingProviderType.LOCAL,
                                onClick = { onSelectEmbeddingProvider(EmbeddingProviderType.LOCAL) }
                            )
                            ProviderOption(
                                title = "OpenAI (Remote)",
                                selected = state.selectedEmbeddingProvider == EmbeddingProviderType.OPENAI,
                                onClick = { onSelectEmbeddingProvider(EmbeddingProviderType.OPENAI) }
                            )
                        }
                        Text(
                            text = when (state.selectedEmbeddingProvider) {
                                EmbeddingProviderType.LOCAL -> "On-device active"
                                EmbeddingProviderType.OPENAI -> {
                                    if (state.openAiApiKeyConfigured) {
                                        "API key configured"
                                    } else {
                                        "API key missing"
                                    }
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.selectedEmbeddingProvider == EmbeddingProviderType.OPENAI &&
                                !state.openAiApiKeyConfigured
                            ) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    pickerLauncher.launch(arrayOf("*/*"))
                                },
                                enabled = !state.isImporting && !state.isIndexing
                            ) {
                                Text("Добавить файлы")
                            }
                            Button(
                                onClick = onBuildIndex,
                                enabled = state.documents.isNotEmpty() &&
                                    !state.isIndexing &&
                                    !state.isImporting &&
                                    (state.selectedEmbeddingProvider != EmbeddingProviderType.OPENAI ||
                                        state.openAiApiKeyConfigured)
                            ) {
                                Text(if (state.isIndexing) "Индексация..." else "Построить индекс")
                            }
                        }
                        state.error?.let { errorText ->
                            Text(
                                text = errorText,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                            TextButton(onClick = onClearError) {
                                Text("Скрыть ошибку")
                            }
                        }
                    }
                }
            }

            item {
                Text("Импортированные файлы", style = MaterialTheme.typography.titleMedium)
            }

            items(state.documents, key = { "doc_${it.id}" }) { document ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(document.displayName, style = MaterialTheme.typography.titleSmall)
                        Text("mime: ${document.mimeType ?: "unknown"}")
                        Text("size: ${document.sizeBytes ?: 0} bytes")
                        Text("status: ${document.status.name.lowercase()}")
                        if (!document.lastError.isNullOrBlank()) {
                            Text(
                                text = "error: ${document.lastError}",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    RagFileOpener.openImportedDocument(
                                        context = context,
                                        localPath = document.localPath,
                                        uriString = document.uriString,
                                        mimeType = document.mimeType
                                    )
                                }
                            ) {
                                Text("Открыть")
                            }
                            TextButton(onClick = { onDeleteDocument(document.id) }) {
                                Text("Удалить")
                            }
                        }
                    }
                }
            }

            state.latestIndexingResult?.let { indexing ->
                item {
                    Text("Результат индексации", style = MaterialTheme.typography.titleMedium)
                    Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 1.dp) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Документов: ${indexing.documentsCount}")
                            Text("Fixed chunks: ${indexing.fixedChunksCount}")
                            Text("Structured chunks: ${indexing.structuredChunksCount}")
                            Text("Fixed avg len: ${"%.2f".format(indexing.fixedAverageChunkLength)}")
                            Text("Structured avg len: ${"%.2f".format(indexing.structuredAverageChunkLength)}")
                            Text("Manifest: ${indexing.manifestId}")
                            Text("Provider: ${indexing.embeddingProviderType.name}")
                            Text("Model: ${indexing.embeddingModel}")
                            Text("Report file: ${indexing.exportedJsonPath}")
                            if (!indexing.vectorsExportPath.isNullOrBlank()) {
                                Text("Vectors file: ${indexing.vectorsExportPath}")
                            }
                        }
                    }
                }
            }

            item {
                Text("Сравнение стратегий chunking", style = MaterialTheme.typography.titleMedium)
            }
            state.latestReport?.let { report ->
                item {
                    Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 1.dp) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Fixed-size", style = MaterialTheme.typography.titleSmall)
                            Text("documents: ${report.fixedStats.documentsCount}")
                            Text("chunks: ${report.fixedStats.chunksCount}")
                            Text("avg chunk length: ${"%.2f".format(report.fixedStats.averageChunkLength)}")
                            Text("max chunk length: ${report.fixedStats.maxChunkLength}")
                        }
                    }
                }
                item {
                    Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 1.dp) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Structure-aware", style = MaterialTheme.typography.titleSmall)
                            Text("documents: ${report.structuredStats.documentsCount}")
                            Text("chunks: ${report.structuredStats.chunksCount}")
                            Text("avg chunk length: ${"%.2f".format(report.structuredStats.averageChunkLength)}")
                            Text("max chunk length: ${report.structuredStats.maxChunkLength}")
                        }
                    }
                }
                item {
                    Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 1.dp) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val chunkDelta = report.structuredStats.chunksCount - report.fixedStats.chunksCount
                            val avgDelta = report.structuredStats.averageChunkLength - report.fixedStats.averageChunkLength
                            Text("Дельта structured - fixed", style = MaterialTheme.typography.titleSmall)
                            Text("chunks delta: $chunkDelta")
                            Text("avg length delta: ${"%.2f".format(avgDelta)}")
                            Text("builtAt: ${formatTimestamp(report.builtAt)}")
                        }
                    }
                }

                item {
                    Text("Per-document chunks", style = MaterialTheme.typography.titleSmall)
                }
                val docIds = (report.fixedStats.perDocumentChunkCount.keys + report.structuredStats.perDocumentChunkCount.keys).toSet().toList().sorted()
                items(docIds, key = { "per_doc_$it" }) { docId ->
                    Surface(shape = RoundedCornerShape(10.dp), tonalElevation = 1.dp) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text("documentId: $docId", style = MaterialTheme.typography.bodySmall)
                            Text("fixed: ${report.fixedStats.perDocumentChunkCount[docId] ?: 0}")
                            Text("structured: ${report.structuredStats.perDocumentChunkCount[docId] ?: 0}")
                        }
                    }
                }
            } ?: item {
                Text(
                    "Сравнение пока недоступно. Построй индекс хотя бы один раз.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            item {
                Text("Экспортированные результаты (.txt)", style = MaterialTheme.typography.titleMedium)
            }

            items(state.exports, key = { "export_${it.exportId}" }) { export ->
                Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 1.dp) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(export.fileName, style = MaterialTheme.typography.titleSmall)
                        Text("manifest: ${export.manifestId}")
                        Text("created: ${formatTimestamp(export.createdAt)}")
                        Text(export.localPath, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    RagFileOpener.openExportedJson(context, export.localPath)
                                }
                            ) {
                                Text("Открыть файл")
                            }
                            TextButton(onClick = { onDeleteExport(export.exportId) }) {
                                Text("Удалить")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderOption(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(onClick = onClick) {
        RadioButton(selected = selected, onClick = null)
        Text(title, modifier = Modifier.padding(start = 6.dp))
    }
}

private fun formatTimestamp(ts: Long): String {
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(ts))
    }.getOrDefault(ts.toString())
}
