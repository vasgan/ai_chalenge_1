package com.example.vasganchalenge1.rag.model

import java.util.UUID

enum class RagDocumentStatus {
    IMPORTED,
    INDEXED,
    ERROR
}

data class RagDocumentFile(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val mimeType: String?,
    val localPath: String?,
    val uriString: String?,
    val status: RagDocumentStatus,
    val sizeBytes: Long?,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class RawDocument(
    val source: String,
    val filePath: String,
    val title: String,
    val text: String,
    val pageCount: Int? = null
)

data class DocumentChunk(
    val chunkId: String,
    val source: String,
    val file: String,
    val title: String?,
    val section: String?,
    val strategy: String,
    val page: Int?,
    val startOffset: Int?,
    val endOffset: Int?,
    val text: String,
    val documentId: String
)

data class ChunkingStats(
    val strategy: String,
    val documentsCount: Int,
    val chunksCount: Int,
    val averageChunkLength: Double,
    val maxChunkLength: Int,
    val perDocumentChunkCount: Map<String, Int>
)

data class ChunkingComparisonReport(
    val fixedStats: ChunkingStats,
    val structuredStats: ChunkingStats,
    val builtAt: Long,
    val exportedJsonPath: String? = null
)

data class RagExportedResult(
    val exportId: String,
    val manifestId: String,
    val fileName: String,
    val localPath: String,
    val createdAt: Long
)

data class RagIndexingResult(
    val manifestId: String,
    val documentsCount: Int,
    val fixedChunksCount: Int,
    val structuredChunksCount: Int,
    val fixedAverageChunkLength: Double,
    val structuredAverageChunkLength: Double,
    val builtAt: Long,
    val exportedJsonPath: String,
    val vectorsExportPath: String?,
    val embeddingEngine: String
)

data class EmbeddedChunk(
    val chunk: DocumentChunk,
    val embedding: FloatArray
)

data class PdfExtractionResult(
    val text: String,
    val pageCount: Int?
)
