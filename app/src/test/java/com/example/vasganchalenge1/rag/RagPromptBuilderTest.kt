package com.example.vasganchalenge1.rag

import com.example.vasganchalenge1.rag.domain.retrieval.RagPromptBuilder
import com.example.vasganchalenge1.rag.model.RetrievedChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RagPromptBuilderTest {

    private val builder = RagPromptBuilder()

    @Test
    fun `build includes metadata and sources`() {
        val payload = builder.build(
            chunks = listOf(
                chunk(id = "c1", text = "first"),
                chunk(id = "c2", text = "second")
            ),
            maxChars = 1000
        )

        assertTrue(payload.contextText.contains("[RAG_CONTEXT]"))
        assertTrue(payload.contextText.contains("chunk_id: c1"))
        assertTrue(payload.contextText.contains("file: file-1.md"))
        assertEquals(2, payload.sources.size)
        assertEquals("c1", payload.sources.first().chunkId)
    }

    @Test
    fun `build respects max chars and truncates chunks`() {
        val payload = builder.build(
            chunks = listOf(
                chunk(id = "c1", text = "a".repeat(80)),
                chunk(id = "c2", text = "b".repeat(80)),
                chunk(id = "c3", text = "c".repeat(80))
            ),
            maxChars = 190
        )

        assertFalse(payload.contextText.contains("chunk_id: c3"))
        assertTrue(payload.sources.size < 3)
    }

    private fun chunk(id: String, text: String): RetrievedChunk {
        return RetrievedChunk(
            chunkId = id,
            score = 0.9f,
            text = text,
            source = "source",
            file = "file-1.md",
            section = "Section A"
        )
    }
}
