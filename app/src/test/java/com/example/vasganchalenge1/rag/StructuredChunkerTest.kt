package com.example.vasganchalenge1.rag

import com.example.vasganchalenge1.rag.domain.chunking.FixedSizeChunker
import com.example.vasganchalenge1.rag.domain.chunking.StructuredChunker
import com.example.vasganchalenge1.rag.model.RawDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredChunkerTest {

    @Test
    fun `splits markdown by headings`() {
        val chunker = StructuredChunker(FixedSizeChunker())
        val raw = RawDocument(
            source = "md",
            filePath = "/tmp/readme.md",
            title = "readme.md",
            text = """
                # Intro
                Hello world

                ## Details
                More details

                ### Result
                Final block
            """.trimIndent()
        )

        val chunks = chunker.chunk(documentId = "doc_md", document = raw)

        assertTrue(chunks.isNotEmpty())
        assertEquals("structured", chunks.first().strategy)
        assertTrue(chunks.any { it.section?.contains("Intro") == true })
    }

    @Test
    fun `splits code by declarations`() {
        val chunker = StructuredChunker(FixedSizeChunker())
        val raw = RawDocument(
            source = "code",
            filePath = "/tmp/Main.kt",
            title = "Main.kt",
            text = """
                class Main {
                    fun one() = Unit
                }

                fun top() = Unit
            """.trimIndent()
        )

        val chunks = chunker.chunk(documentId = "doc_code", document = raw)

        assertTrue(chunks.size >= 2)
        assertTrue(chunks.any { it.section?.contains("class Main") == true })
    }
}
