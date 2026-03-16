package com.example.vasganchalenge1.rag.domain.chunking

import com.example.vasganchalenge1.rag.model.DocumentChunk
import com.example.vasganchalenge1.rag.model.RawDocument
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FixedSizeChunker @Inject constructor() {

    fun chunk(
        documentId: String,
        document: RawDocument,
        chunkSizeChars: Int = 1200,
        overlapChars: Int = 200
    ): List<DocumentChunk> {
        if (document.text.isBlank()) return emptyList()

        val safeChunkSize = chunkSizeChars.coerceAtLeast(200)
        val safeOverlap = overlapChars.coerceIn(0, safeChunkSize / 2)
        val chunks = mutableListOf<DocumentChunk>()

        var start = 0
        var part = 0
        val text = document.text
        while (start < text.length) {
            val end = (start + safeChunkSize).coerceAtMost(text.length)
            val chunkText = text.substring(start, end).trim()
            if (chunkText.isNotBlank()) {
                chunks += DocumentChunk(
                    chunkId = "fixed_${UUID.randomUUID()}",
                    source = document.source,
                    file = document.filePath,
                    title = document.title,
                    section = "part_${part + 1}",
                    strategy = STRATEGY,
                    page = estimatePage(document.text, start, document.pageCount),
                    startOffset = start,
                    endOffset = end,
                    text = chunkText,
                    documentId = documentId
                )
            }
            if (end >= text.length) {
                break
            }
            start = (end - safeOverlap).coerceAtLeast(start + 1)
            part += 1
        }

        return chunks
    }

    private fun estimatePage(fullText: String, startOffset: Int, pageCount: Int?): Int? {
        if (pageCount == null || pageCount <= 1) return null
        val prefix = fullText.take(startOffset)
        val pagesBefore = prefix.count { it == '\u000C' }
        return (pagesBefore + 1).coerceIn(1, pageCount)
    }

    companion object {
        const val STRATEGY = "fixed"
    }
}
