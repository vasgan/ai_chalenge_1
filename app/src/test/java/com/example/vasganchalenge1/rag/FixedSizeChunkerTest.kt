package com.example.vasganchalenge1.rag

import com.example.vasganchalenge1.rag.domain.chunking.FixedSizeChunker
import com.example.vasganchalenge1.rag.model.RawDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FixedSizeChunkerTest {

    @Test
    fun `chunks text with overlap and metadata`() {
        val chunker = FixedSizeChunker()
        val text = buildString {
            repeat(300) { append("a") }
            repeat(300) { append("b") }
            repeat(300) { append("c") }
        }
        val raw = RawDocument(
            source = "test",
            filePath = "/tmp/test.txt",
            title = "test.txt",
            text = text
        )

        val chunks = chunker.chunk(
            documentId = "doc_1",
            document = raw,
            chunkSizeChars = 400,
            overlapChars = 100
        )

        assertTrue(chunks.size >= 2)
        assertEquals("fixed", chunks.first().strategy)
        assertEquals("doc_1", chunks.first().documentId)
        assertTrue(chunks.first().text.isNotBlank())
    }
}
