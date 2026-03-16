package com.example.vasganchalenge1.rag

import com.example.vasganchalenge1.rag.domain.ChunkingComparisonService
import com.example.vasganchalenge1.rag.model.DocumentChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChunkingComparisonServiceTest {

    @Test
    fun `builds report with aggregated metrics`() {
        val service = ChunkingComparisonService()
        val fixed = listOf(
            chunk("f1", "fixed", "doc1", "hello"),
            chunk("f2", "fixed", "doc1", "world!!!")
        )
        val structured = listOf(
            chunk("s1", "structured", "doc1", "abc"),
            chunk("s2", "structured", "doc2", "defgh")
        )

        val report = service.buildReport(fixed, structured, builtAt = 1L)

        assertEquals(2, report.fixedStats.chunksCount)
        assertEquals(2, report.structuredStats.chunksCount)
        assertEquals(2, report.structuredStats.documentsCount)
        assertTrue(report.fixedStats.averageChunkLength > 0)
        assertEquals(1L, report.builtAt)
    }

    private fun chunk(id: String, strategy: String, docId: String, text: String): DocumentChunk {
        return DocumentChunk(
            chunkId = id,
            source = "source",
            file = "/tmp/file",
            title = "title",
            section = "section",
            strategy = strategy,
            page = null,
            startOffset = 0,
            endOffset = text.length,
            text = text,
            documentId = docId
        )
    }
}
