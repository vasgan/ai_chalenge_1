package com.example.vasganchalenge1.rag.domain

import com.example.vasganchalenge1.rag.model.ChunkingComparisonReport
import com.example.vasganchalenge1.rag.model.ChunkingStats
import com.example.vasganchalenge1.rag.model.DocumentChunk
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChunkingComparisonService @Inject constructor() {

    fun buildReport(
        fixedChunks: List<DocumentChunk>,
        structuredChunks: List<DocumentChunk>,
        builtAt: Long = System.currentTimeMillis()
    ): ChunkingComparisonReport {
        return ChunkingComparisonReport(
            fixedStats = toStats("fixed", fixedChunks),
            structuredStats = toStats("structured", structuredChunks),
            builtAt = builtAt
        )
    }

    private fun toStats(strategy: String, chunks: List<DocumentChunk>): ChunkingStats {
        val chunksCount = chunks.size
        val lengths = chunks.map { it.text.length }
        val average = if (lengths.isEmpty()) 0.0 else lengths.average()
        val max = lengths.maxOrNull() ?: 0
        val perDocument = chunks.groupingBy { it.documentId }.eachCount()

        return ChunkingStats(
            strategy = strategy,
            documentsCount = perDocument.size,
            chunksCount = chunksCount,
            averageChunkLength = average,
            maxChunkLength = max,
            perDocumentChunkCount = perDocument
        )
    }
}
