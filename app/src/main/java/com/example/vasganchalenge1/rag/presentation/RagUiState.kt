package com.example.vasganchalenge1.rag.presentation

import com.example.vasganchalenge1.rag.model.ChunkingComparisonReport
import com.example.vasganchalenge1.rag.model.RagDocumentFile
import com.example.vasganchalenge1.rag.model.RagExportedResult
import com.example.vasganchalenge1.rag.model.RagIndexingResult

enum class RagScreenStatus {
    IDLE,
    IMPORTING,
    READY_TO_INDEX,
    INDEXING,
    INDEXED,
    ERROR
}

data class RagUiState(
    val status: RagScreenStatus = RagScreenStatus.IDLE,
    val documents: List<RagDocumentFile> = emptyList(),
    val latestReport: ChunkingComparisonReport? = null,
    val exports: List<RagExportedResult> = emptyList(),
    val latestIndexingResult: RagIndexingResult? = null,
    val isImporting: Boolean = false,
    val isIndexing: Boolean = false,
    val error: String? = null
)
